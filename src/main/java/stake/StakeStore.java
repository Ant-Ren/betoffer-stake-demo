package stake;

import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Stores the total (sum) stake per customer per betting offer.
 * When a customer places multiple bets on the same offer, stakes are accumulated.
 * Supports retrieving top N highest total stakes per offer.
 */
final class StakeStore {

    /** Entry in sorted set: (customerId, stake). Equality by both fields for correct remove. */
    private static final class StakeEntry {
        final int customerId;
        final int stake;

        StakeEntry(int customerId, int stake) {
            this.customerId = customerId;
            this.stake = stake;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StakeEntry)) return false;
            StakeEntry that = (StakeEntry) o;
            return customerId == that.customerId && stake == that.stake;
        }

        @Override
        public int hashCode() {
            return (31 * customerId + stake) % Integer.MAX_VALUE; // Avoid overflow
        }
    }

    // Per-offer: map for total stake lookup + set ordered by total stake desc, then customerId asc
    private static final class PerOfferData {
        final ConcurrentHashMap<Integer, Integer> customerToStake = new ConcurrentHashMap<>();
        final ConcurrentSkipListSet<StakeEntry> sortedStakes = new ConcurrentSkipListSet<>(
            Comparator.<StakeEntry>comparingInt(e -> e.stake).reversed()
        );
    }

    // betOfferId -> per-offer data (map + sorted set)
    private final ConcurrentHashMap<Integer, PerOfferData> offerToData = new ConcurrentHashMap<>();

    void addStake(int betOfferId, int customerId, int stake) {
        System.out.println("Adding stake: " + betOfferId + " for customer " + customerId + " with stake " + stake);
        PerOfferData data = offerToData.computeIfAbsent(betOfferId, k -> new PerOfferData());
        synchronized (data) {
            Integer oldTotal = data.customerToStake.get(customerId);
            int newTotal = (oldTotal == null ? 0 : oldTotal) + stake;
            if (oldTotal != null) {
                data.sortedStakes.remove(new StakeEntry(customerId, oldTotal));
            }
            data.customerToStake.put(customerId, newTotal);
            data.sortedStakes.add(new StakeEntry(customerId, newTotal));
        }
    }

    /**
     * Returns top N highest stakes for the offer, one per customer.
     * Format: "customerId=stake,customerId=stake,..."
     * Returns empty string if no data.
     */
    String getHighStakesCsv(int betOfferId, int topN) {
        System.out.println("Getting high stakes for offer " + betOfferId + " with top " + topN);
        PerOfferData data = offerToData.get(betOfferId);
        if (data == null || data.sortedStakes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        int cutoffStake = 0; // stake of the Nth entry; lock this value to avoid duplicate entries
        for (StakeEntry e : data.sortedStakes) {
            if (count < topN) {
                count++;
                if (count == topN) cutoffStake = e.stake;
                if (count > 1) sb.append(',');
                sb.append(e.customerId).append('=').append(e.stake);
            } else {
                if (e.stake != cutoffStake) break;
                sb.append(',').append(e.customerId).append('=').append(e.stake);
            }
        }
        return sb.toString();
    }
}

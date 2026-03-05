## Requirement

- This is a HTTP-based back-end which stores and provides betting offer's stakes for different customers, with the capability to return the highest stakes on a betting offer.
- The service needs to be able to handle a lot of simultaneous requests, so bear in mind the memory and CPU resources at your disposal.
- Do not use any external frameworks, except maybe for testing. You can use com.sun.net.httpserver.HttpServer for the http server.

## Solution Design

### Security
- Considering the service is used by Casino businesses, so **safety mindset** is a critical rule during design and coding.
- Always choose **thread-safe** classes/libs to process or store data.
  - Use SecureRandom instead of Random to generate session keys.
  - Use ConcurrentHashMap/ConcurrentSkipListSet to store data.
  - Use synchronized to handle key data-write logic.
- Using Java's internal HTTP server class, firstly build **SecurityGate** to enhance network security in three ways (DO NOT trust any input data/info):
  - Request path check for any abnormal or insecure access.
  - Request header check for any potential attacks.
  - Request parameter check for any illegal characters or injections.
- Record suspicious unsafe events with source data such as IP address, type, and detailed information.

### Business Logic
- The key logic is to get the top 20 stakes per bet offer, and a customer can post additional stakes for the same betting offer. Also, a customer ID can appear at most once in the top 20 stakes. This alerts me to pay attention to these scenarios:
  - When a customer posts a stake for the same bet multiple times, the system should calculate the total stake for this customer.
  - During the top 20 stakes sorting, what if the last position includes multiple customers, such as 3 customers who all placed a stake of 100 and 100 is the 20th stake? Following common risk-management sense, since top stakes are about alerting the company to potential large losses, I chose to implement it by returning all the stakes at the 20th position, which means the list of top stakes may be longer than 20.
  - Besides, if the purpose is for user recommendation, then we may have a different implementation.

### Performance
- Requrement mentioned the service need to be able to handle a lot of simultaneous requests, so business logic implementation should also consider the performance seriously.
  - After analysis, the bottleneck of system performance mainly occurs in the sorting of top N stakes. If new stakes are stored in chronological order and then sorted during retrieval, we first need to sort by each user's total stake and then pick the top N, resulting in a time complexity of O(N log n). However, if we **pre-sort stakes in descending order upon each new bet entry, the complexity of fetching the top N stakes can be reduced to O(N)**. This represents a trade-off in performance distribution.
  - Ultimately, I chose the pre-sorting strategy: when each customer places a bet on an offer, the stakes are sorted in descending order in advance. I selected **ConcurrentSkipListSet** (using a skip list algorithm) to ensure that the time complexity for stake sorting can reach O(log n). This approach sacrifices some performance during stake intake but offers much better performance for real-time fetching and updating of the top N stakes. From a casino business risk management perspective, I believe this trade-off is more beneficial than harmful.
  - In real production environment, cloud service(redis, redshift e.g.) or serverless technologies can be leveraged to effectively increase the system’s throughput to handle massive parallel betting from users.

### Engineering
- Please note: **This project was developed through pair programming with AI**, with approximately 60%-70% of the code contributed by AI (using the tool: Cursor).
- The entire project follows the **Single Responsibility Principle**（SRP）:
  - SecurityGate is dedicated to intrusion and abnormal request detection (typically implemented as a singleton).
  - SessionStore is dedicated to issuing and managing session keys (typically implemented as a singleton).
  - StakeStore is dedicated to receiving and managing bet offers/stakes from different customers (with extensibility reserved).
- To ensure the results meet the business requirements, which I evaluate AI could not handle, I generated a variety of test data covering different scenarios, including:
  - Bets with both large and small amounts, and customers betting on a variable number of bet offers
  - A single user placing multiple bets on the same bet offer, or multiple users betting on the same bet offer (e.g., betOfferId = 32)
  - In the top 20 sorting, there may be several users sharing the 20th rank, which results in the actual returned list being longer than 20 entries (e.g., betOfferId = 69)
- I created a `test` directory and randomly generated a sample database `sample-data.txt` to cover diverse scenarios; I also created the `SampleInitializer` tool to quickly import over 1500 samples for efficient verification of interface logic and further research/optimization (see Build & Run below for usage instructions).

## Build & Run

```bash
# Compile if jar file doesn't exist or you want to rebuild
./compile.sh

# Run jar file (default port 8001)
./run.sh

# Build & Run & Import sample test data
./test/run-initializer.sh
```

<h1 align="center">Realtime Online Auction System</h1>

<p align="center">
  <img src="assets/image.png" width="900"/>
</p>

## Overview

🚀 A real-time online auction platform built with Java 21, following a clean Client–Server architecture. The Spring Boot server owns all business logic, concurrency control, and database access, while the JavaFX client provides a responsive graphical interface for three distinct user roles: 🧑‍💼 Bidder, 🛍️ Seller, and 🛡️ Admin.

⏳ Auction lifecycle. Each auction session follows a strict state machine: OPEN → RUNNING → FINISHED → PAID / CANCELED. Transitions are triggered automatically by a server-side scheduler, ensuring that sessions open and close exactly on time without any manual intervention. ⏰

🔄 Real-time updates. Whenever a bid is placed, every client currently viewing that auction instantly receives the updated price, the new leading bidder, and the remaining time — pushed directly from the server through a persistent WebSocket connection using the STOMP protocol. No polling. No refresh. No delay. ⚡

🛡️ Concurrent safety. The system is designed to handle multiple simultaneous bids without race conditions or lost updates, using a combination of JPA optimistic locking (@Version) and pessimistic locking (SELECT FOR UPDATE) to guarantee that exactly one bid wins at any given moment. 🔐

🤖 Advanced features. Users can register an auto-bid with a maximum price and a step increment, and the system will automatically bid on their behalf whenever they are outbid, resolving conflicts by maximum price first and then by registration time. A built-in anti-sniping algorithm extends the auction end time whenever a valid bid arrives within the final 30 seconds, preventing last-second sniping. A live price-curve chart in the auction detail view plots each accepted bid as it arrives, giving all participants a clear visual history of how the price has evolved over time. 📈

🏗️ Design. The codebase applies OOP principles throughout: a clear inheritance hierarchy (Entity → User / Item → role and category subclasses), strong encapsulation enforced through access modifiers and DTOs, and polymorphism via abstract methods and interfaces. Three design patterns are applied deliberately: Singleton for the auction session manager, Factory Method for item creation by category, and Observer to decouple bid events from WebSocket broadcasting and notification persistence. 🧩

🧰 Tooling. Built with Gradle in a multi-module structure, with server and client as independent modules; tested using JUnit 5 and Mockito, with service-layer coverage ≥ 80% enforced by JaCoCo; and delivered through a CI/CD pipeline on GitHub Actions that builds, tests, and checks coding conventions on every push. ✅

## Scoring
Below is the grading rubric for this project.

<p align="center">
  <img src="assets/image1.png" width="515"/>
</p>

## 📈 Commit History (Repo Only)

![Commit Graph](./scripts/commit-chart.svg)


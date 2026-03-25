<h1 align="center">Realtime Online Auction System</h1>

<p align="center">
  <img src="assets/image.png" width="900"/>
</p>

## Overview

A real-time online auction platform built with Java 21, following a clean Client–Server architecture. The Spring Boot server owns all business logic, concurrency control, and database access; the JavaFX client delivers a responsive graphical interface for three distinct user roles — Bidder, Seller, and Admin.

Auction lifecycle. Each session progresses through a well-defined state machine: OPEN → RUNNING → FINISHED → PAID or CANCELED. Transitions are driven automatically by a server-side scheduler, ensuring sessions open and close precisely on time without manual intervention.

Real-time updates. When a bid is placed, every client currently viewing that session receives the updated price, new leader, and remaining time instantly — pushed by the server over a persistent WebSocket connection using the STOMP protocol. No polling, no page refresh.

Concurrent safety. The system handles multiple simultaneous bids without race conditions or lost updates, using a combination of JPA optimistic locking (@Version) and pessimistic locking (SELECT FOR UPDATE) to guarantee that exactly one bid wins at any given moment.

Advanced features. Users can register an auto-bid with a maximum price and a step increment — the system then bids on their behalf automatically whenever they are outbid, resolving conflicts by maximum price then registration time. An anti-sniping algorithm extends the session end time whenever a valid bid arrives within the final 30 seconds, preventing last-second sniping. A live price-curve chart in the auction detail view plots each accepted bid as it arrives, giving all participants a clear view of how the price has evolved over time.

Design. The codebase applies OOP principles throughout: a clear inheritance hierarchy (Entity → User / Item → role and category subclasses), encapsulation enforced via access modifiers and DTOs, and polymorphism through abstract methods and interfaces. Three design patterns are applied deliberately — Singleton for the auction session manager, Factory Method for item creation by category, and Observer to decouple bid events from WebSocket broadcasting and notification persistence.

Tooling. Built with Gradle (multi-module — server and client as independent modules), tested with JUnit 5 and Mockito (service-layer coverage ≥ 80% enforced by JaCoCo), and delivered with a CI/CD pipeline on GitHub Actions that builds, tests, and checks coding convention on every push.

## Scoring
Below is the grading rubric for this project.

<p align="center">
  <img src="assets/image1.png" width="515"/>
</p>

## 📈 Commit History (Repo Only)

![Commit Graph](./scripts/commit-chart.svg)


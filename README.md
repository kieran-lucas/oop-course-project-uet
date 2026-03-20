<h1 align="center">Realtime Online Auction System</h1>

<p align="center">
  <img src="assets/image.png" width="900"/>
</p>

## Overview

A real-time online auction platform built with Java, following a Client–Server 
architecture. The server handles all business logic, concurrency, and database 
access; the JavaFX client provides a graphical interface for three user roles — 
Bidder, Seller, and Admin.

Core features include auction session lifecycle management (OPEN → RUNNING → 
FINISHED → PAID/CANCELED), real-time bid updates pushed to all connected clients 
via Socket, and thread-safe concurrent bidding to prevent race conditions and lost 
updates.

Advanced features include auto-bidding (users set a max price and the system bids 
on their behalf), an anti-sniping algorithm (last-second bids automatically extend 
the session), and a real-time price curve chart that updates live with each new bid.

The system applies OOP principles throughout, with a clear class hierarchy 
(Entity → User/Item → role and category subclasses), and implements the Singleton, 
Factory Method, and Observer design patterns. Built with Maven, tested with JUnit, 
and deployed with a CI/CD pipeline via GitHub Actions.

## Scoring
Below is the grading rubric for this project.

<p align="center">
  <img src="assets/image1.png" width="600"/>
</p>





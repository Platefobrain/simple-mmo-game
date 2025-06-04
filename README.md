# Kotlin MMO Game (LibGDX + Ktor)

A simple 2D multiplayer online RPG built with Kotlin using LibGDX for the client and Ktor for the server.  
Players can log in, create or select characters, move, fight, chat, and level up in real-time through WebSocket communication.

---

## Features

- **Player authentication** with multi-character support (max 3 characters per account)  
- **Character creation, selection, and persistence** via JSON storage  
- **Real-time movement** with server-side validation and A* pathfinding  
- **AI-driven enemies** with patrol, chase, and return behaviors  
- **Class-based combat system** (Archer, Mage, Warrior) with various skills and projectiles  
- **Leveling system** for players and enemies with scaling stats  
- **Integrated chat system** with message history and broadcasting  
- **UI implemented with LibGDX scene2d** (login, character screens, death screen)  
- **Thread-safe, modular server architecture** with strategy and factory patterns  

---

## Tech Stack

- **Client:** Kotlin + LibGDX  
- **Server:** Kotlin + Ktor WebSockets  
- **Networking:** WebSocket with JSON serialization  
- **Persistence:** JSON file storage (auto-save)  
- **AI:** Behavior-based enemy AI using state machines and pathfinding  

---

![character_select](https://github.com/user-attachments/assets/9899cc8e-88b3-4f78-a8cd-098edb64d6fd)
![game](https://github.com/user-attachments/assets/d02c3e9a-8687-4437-a160-cf128019eae3)


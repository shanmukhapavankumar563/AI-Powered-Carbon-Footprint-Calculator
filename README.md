# AI-Powered-Carbon-Footprint-Calculator

An AI-assisted carbon footprint calculator with:
- React + Vite frontend
- Node.js server APIs
- Spring Boot backend services

## Repository Structure

```text
AI-Powered-Carbon-Footprint-Calculator/
  carbon-wise-ai-friend/
    src/                 # Frontend source code
    server/              # Node.js backend
    spring-backend/      # Spring Boot backend
```

## Tech Stack

- Frontend: React, TypeScript, Vite, Tailwind CSS
- Node Backend: Express/Node (inside `server/`)
- Java Backend: Spring Boot 3, Java 17, Maven
- Database: MySQL (configured in Spring backend)

## Prerequisites

- Node.js 18+ and npm
- Java 17+
- Maven 3.9+
- MySQL 8+

## Setup

1. Clone the repository:

```bash
git clone https://github.com/shanmukhapavankumar563/AI-Powered-Carbon-Footprint-Calculator.git
cd AI-Powered-Carbon-Footprint-Calculator/carbon-wise-ai-friend
```

2. Install frontend dependencies:

```bash
npm install
```

3. Install server dependencies:

```bash
cd server
npm install
cd ..
```

4. Configure backend:
- Update database and API settings in:
`carbon-wise-ai-friend/spring-backend/src/main/resources/application.properties`

## Run the Project

From `carbon-wise-ai-friend/`:

- Run frontend:

```bash
npm run dev
```

- Run Node server:

```bash
npm run server
```

- Run frontend + Node server together:

```bash
npm run dev:full
```

- Run Spring backend:

```bash
npm run spring
```

## Build for Production

```bash
npm run build
```

## Git Workflow

After making changes:

```bash
git add .
git commit -m "Describe your change"
git push
```

## Release Tag

Current tag: `v1.0.0`

## License

Add a `LICENSE` file (MIT recommended) if you plan to make this open-source.

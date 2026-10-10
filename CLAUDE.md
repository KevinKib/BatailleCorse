# BatailleCorse : consignes pour Claude

Jeux de cartes temps réel (Bataille Corse et Bullshit) : backend Spring Boot (compilé en `-source/-target 17` selon le `pom.xml`, exécuté sur JDK 22 dans le Dockerfile et la CI ; WebSocket/STOMP, état en mémoire, pas de base de données) et frontend Vue 3 + Vite + Pinia + PrimeVue, orchestrés avec Docker Compose. Les commandes détaillées sont dans le `README.md`. Les règles communes à tous les projets (worktrees, conventions git, vérifications, workflow) sont dans le `CLAUDE.md` global de l'utilisateur.

## Langue

- Code, commentaires, messages de commit, titres de PR, docs existantes, specs et plans (`docs/specs/`, `docs/plans/`) : en anglais.
- Réponses à l'utilisateur et descriptions de PR : en français.
- Specs dans `docs/specs/`, plans dans `docs/plans/`, architecture dans `docs/architecture/`.

## Architecture et règles du domaine

- Backend en hexagonal, un contexte borné par jeu : `bataillecorse`, `bullshit` (règles pures, dans `domain/`), `sessionmanagement` (`core` : sièges, jetons, création/jonction/démarrage/revanche ; `presence` : connexion, délai de grâce, forfait) et un noyau partagé `game` (`Game`, `GameFactory`, `GameId`, `PlayerId`). Carte : `docs/architecture/context-map.md`.
- Les règles métier vivent dans le domaine (`<jeu>/domain`), jamais dans les contrôleurs (`presentation`) ni dans le frontend. `sessionmanagement` ne dépend que du noyau `game`, jamais d'un jeu concret ; un jeu ne connaît ni sessions, ni jetons, ni transport.
- Une action est authentifiée par le jeton de siège (`SessionToken`) ; les DTO et les événements WebSocket sont dans `presentation/`.
- Frontend : Composition API avec `<script setup>` et TypeScript (skill `vue-best-practices`). Tout nouveau texte visible passe par `frontend/src/locales/`.
- Dépendance privée `org.kevinkib:frenchcards` (GitHub Packages) : elle se résout avec `deploy/docker/settings.xml` (ignoré par git, voir le README). Sans ce fichier, rien ne se construit.

## Suivi

- Roadmap : issue #1 ; sessions : #5 (fermée) ; e2e : #28 ; démarrage de dev : #54. Cocher ou commenter l'issue quand une PR est mergée ; aucune PR de statut.
- Specs dans `docs/specs/`, plans dans `docs/plans/`, évolutions du harness dans `docs/harnesses.md`.

## Tests (toutes les suites avant une PR)

- Backend : `cd backend && mvn -s ../deploy/docker/settings.xml test` (si Maven est installé en local ; sinon via le conteneur de dev, voir le README). Surefire est configuré dans `backend/pom.xml` pour ramasser aussi `**/*IT.java` (par défaut il les ignore) : `mvn test` joue donc les tests unitaires et les `*IT` / `ApplicationContextTest`, qui démarrent le contexte Spring sans Docker (pas de failsafe : aucune phase `verify` nécessaire). `BatailleCorseWebSocketControllerIT` est `@Disabled` (périmé, à réécrire). Un nouveau test d'intégration doit se nommer `*IT` ou `*Test`, sinon il ne tourne pas.
- Frontend : `cd frontend && npm test` (Vitest + happy-dom), puis `npx vite build`.
- CI (`.github/workflows/ci.yml`, push sur `main` et pull requests, ubuntu-latest) : rejoue ces deux suites (backend `mvn -B test` avec JDK 22, frontend `npm ci`, `npm test`, `npx vite build` avec Node 20). Dans la CI, `frenchcards` se résout via le `GITHUB_TOKEN` (pas de `settings.xml` du dépôt). Pas d'e2e en CI pour l'instant.
- E2E (Cypress) : pile `docker-compose.e2e.yml` (profil Spring `test`), puis `cd frontend && npm run cy:run`. Cypress vise le port fixe 5173 : une seule pile e2e à la fois. Si une autre pile tient ce port, l'arrêter (`docker stop`, jamais de suppression), jouer l'e2e, démonter la pile e2e (`docker compose -f docker-compose.e2e.yml down`), puis redémarrer la pile arrêtée, même si Cypress échoue.

## Environnement de dev

- Pas de base de données, donc pas de jeu de données à charger : l'état vit en mémoire, une partie se crée depuis l'interface (deux onglets ou deux navigateurs pour deux joueurs).
- Seul : `sh dev.sh` (équivalent de `docker compose -f docker-compose.dev.yml up --build`). Frontend http://localhost:5173, backend http://localhost:8080, débogage distant 5005. Le backend a un healthcheck : ajouter `-d --wait` pour l'attendre en arrière-plan.
- Le Vite du conteneur proxifie `/api` et `/connect` vers `backend:8080` sur le réseau Compose : on ne lance donc pas Vite hors de Docker.
- Ports pris par une autre session : la laisser tourner et lancer celle-ci sous son propre nom de projet avec un fichier de ports décalés (dans le scratchpad, pas dans le dépôt). `VITE_HMR_CLIENT_PORT` garde le rechargement à chaud sur le port publié.

  ```yaml
  # ports.yml : décalage de +2 (vérifier avec docker ps que les ports sont libres)
  services:
    backend:
      ports: !override ["8082:8080", "5007:5005"]
    frontend:
      ports: !override ["5175:5173"]
      environment:
        - VITE_HMR_CLIENT_PORT=5175
  ```

  ```sh
  docker compose -p bc-<sujet> -f docker-compose.dev.yml -f <scratchpad>/ports.yml up --build -d --wait
  ```

  URL : http://localhost:5175. Pour l'e2e sur une pile décalée, Cypress doit viser son port : `npx cypress run --config baseUrl=http://localhost:5175`, ce qui évite d'arrêter la pile de l'autre session.
- Production : derrière le gateway partagé (`C:/Users/kevin/Documents/Git/gateway`, réseau Docker `web`) ; ne jamais lancer `prod.sh` depuis une session de dev.

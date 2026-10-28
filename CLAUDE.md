# BatailleCorse : consignes pour Claude

Jeux de société temps réel (Bataille Corse, Bullshit, Kobo, President , la marelle à trois pions, Three Men's Morris, Mangola, le 99, les dames internationales et le Blackjack) : backend Spring Boot (compilé en `-source/-target 17` selon le `pom.xml`, exécuté sur JDK 22 dans le Dockerfile et la CI ; WebSocket/STOMP, état en mémoire, pas de base de données) et frontend Vue 3 + Vite + Pinia + PrimeVue, orchestrés avec Docker Compose. Les commandes détaillées sont dans le `README.md`. Les règles communes à tous les projets (worktrees, conventions git, vérifications, workflow) sont dans le `CLAUDE.md` global de l'utilisateur.

## Langue

- Code, commentaires, messages de commit, titres de PR, docs existantes, specs et plans (`docs/specs/`, `docs/plans/`) : en anglais.
- Réponses à l'utilisateur et descriptions de PR : en français.
- Specs dans `docs/specs/`, plans dans `docs/plans/`, architecture dans `docs/architecture/`.

## Architecture et règles du domaine

- Backend en hexagonal, un contexte borné par jeu : `bataillecorse`, `bullshit`, `kobo`, `threemenmorris` (règles pures, dans `domain/`), `sessionmanagement` (`core` : sièges, jetons, création/jonction/démarrage/revanche ; `presence` : connexion, délai de grâce, forfait) et un noyau partagé `game` (`Game`, `GameFactory`, `GameId`, `PlayerId`). Carte : `docs/architecture/context-map.md`.
- Ajouter un jeu : suivre `docs/architecture/add-a-game.md` et les noms de `docs/architecture/game-conventions.md`.
- Les règles métier vivent dans le domaine (`<jeu>/domain`), jamais dans les contrôleurs (`presentation`) ni dans le frontend. `sessionmanagement` ne dépend que du noyau `game`, jamais d'un jeu concret ; un jeu ne connaît ni sessions, ni jetons, ni transport.
- Une action est authentifiée par le jeton de siège (`SessionToken`) ; les DTO et les événements WebSocket sont dans `presentation/`.
- Frontend : Composition API avec `<script setup>` et TypeScript (skill `vue-best-practices`). Tout nouveau texte visible passe par `frontend/src/locales/`, en anglais (`en.ts`) et en français (`fr/`) ; le test de parité (`parity.test.ts`) échoue si une clé manque en français ou y reste en anglais.
- Dépendance privée `org.kevinkib:frenchcards` (GitHub Packages) : elle se résout avec `deploy/docker/settings.xml` (ignoré par git, voir le README). Sans ce fichier, rien ne se construit.

## Suivi

- Roadmap : issue #1 ; sessions : #5 (fermée) ; e2e : #28 ; démarrage de dev : #54. Cocher ou commenter l'issue quand une PR est mergée ; aucune PR de statut.
- Specs dans `docs/specs/`, plans dans `docs/plans/`, évolutions du harness dans `docs/harnesses.md`.

## Espacement de l'UI

- Tout changement de mise en page, d'espacement (marge, padding, gap) ou tout nouveau composant, contrôle, texte d'aide du frontend suit le skill `frontend-spacing` (`.claude/skills/frontend-spacing/SKILL.md`) : échelle `--space-1`…`--space-8` d'`App.vue`, règles de proximité, check-list avant PR à 375x667 et 1280x720, et `expectGap` / `expectNoOverlap` (`frontend/cypress/support/spacing.ts`) dans la spec de la fonctionnalité. Pourquoi : ces défauts passaient tous les tests et n'étaient vus qu'en jouant.

## Tests (toutes les suites avant une PR)

- Backend : `cd backend && mvn -s ../deploy/docker/settings.xml test` (si Maven est installé en local ; sinon via le conteneur de dev, voir le README). Surefire est configuré dans `backend/pom.xml` pour ramasser aussi `**/*IT.java` (par défaut il les ignore) : `mvn test` joue donc les tests unitaires et les `*IT` / `ApplicationContextTest`, qui démarrent le contexte Spring sans Docker (pas de failsafe : aucune phase `verify` nécessaire). Un nouveau test d'intégration doit se nommer `*IT` ou `*Test`, sinon il ne tourne pas.
- Frontend : `cd frontend && npm test` (Vitest + happy-dom), puis `npx vite build`.
- CI (`.github/workflows/ci.yml`, push sur `main` et pull requests, ubuntu-latest) : rejoue ces deux suites (backend `mvn -B test` avec JDK 22, frontend `npm ci`, `npm test`, `npx vite build`, `npm run cy:component` avec Node 20 ; le binaire Cypress n'est installé dans ce job que pour cette dernière étape). Dans la CI, `frenchcards` se résout via le `GITHUB_TOKEN` (pas de `settings.xml` du dépôt). Le job `E2E smoke (Cypress)` joue le sous-ensemble de `frontend/cypress/smoke-specs.json` sur la pile `docker-compose.e2e.yml` ; ce n'est pas encore un check requis (il le devient quand le journal de runs le montre stable, décision après la lecture à deux semaines de #312). C'est la vérification Cypress des PR : sans pile locale, on lit ce job.
- Avant de merger une PR : si `main` a reçu un merge depuis le dernier run de la CI de la PR et que ce merge a touché un test partagé (`parity.test.ts`, tests d'architecture, `smoke-specs.json`), fusionner `main` dans la branche de la PR et ne merger que lorsque la CI est verte sur ce commit. Après chaque merge, lire la CI de `main` avant de lancer le lot suivant.
- E2E (Cypress) : pile `docker-compose.e2e.yml` (profil Spring `test`) lancée par `docker compose -f docker-compose.e2e.yml up --build -d --wait`, puis `cd frontend && npm run cy:run`. Cette pile n'est pas celle de dev : jar préconstruit (cible `run` du `backend/Dockerfile`, `settings.xml` en secret de build) et build de production servi par `vite preview` (cible `e2e` du `frontend/Dockerfile`), sans montage ni rechargement à chaud. Un changement de code n'y arrive que par `--build` (moins d'une minute ; environ 3 minutes après un changement du `pom.xml` ; environ 25 s sans changement) ; `VITE_HMR_CLIENT_PORT` y est sans effet. Cypress vise le port fixe 5173 : une seule pile e2e à la fois. Si une autre pile tient ce port, l'arrêter (`docker stop`, jamais de suppression), jouer l'e2e, démonter la pile e2e (`docker compose -f docker-compose.e2e.yml down`), puis redémarrer la pile arrêtée, même si Cypress échoue.
- Cypress côté agents (règle de l'issue #312) : un agent ne joue que les specs Cypress de la fonctionnalité qu'il a touchée (celles que son brief désigne comme nouvelles ou modifiées), une seule fois ; une spec en échec se rejoue seule, au plus une fois, pour distinguer une flakiness de charge d'une vraie régression ; une famille entière (tous les `president-*`, tous les `kobo-*`) n'est rejouée que par le chunk d'intégration d'un plan ou à la demande du propriétaire ; le rapport liste les specs jouées et le temps Cypress total. Pourquoi : Cypress pèse 26 % du temps actif des agents (42 % avec les attentes de pile) pour aucune régression rattrapée après coup (`docs/architecture/e2e-cypress-report.md`). La couverture e2e elle-même est conservée : rien n'est supprimé.
- Cypress, outils du plan de coût (#312, `docs/plans/2026-10-28-e2e-cost-plan.md`) : `npm run cy:smoke` joue le sous-ensemble de `frontend/cypress/smoke-specs.json` (seul le chunk d'intégration d'un plan le modifie) ; `npm run cy:component` joue les tests de composants (`frontend/cypress/component/`, sans pile : garde-fous de géométrie d'un état fixe, plus rapides qu'une spec e2e) ; chaque `cypress run` ajoute une ligne JSON par spec au journal `frontend/cypress/results/run-log.jsonl` (durée, échec, tests rejoués par l'unique retry du mode run) et `npm run cy:summary` le résume par spec (runs, échecs, retries, durée médiane).
- Hygiène des specs Cypress : une spec nouvelle ou étendue ne couvre que le chemin nominal de sa fonctionnalité (règle du propriétaire) ; variantes, cas limites et règles de jeu vont aux tests Vitest ou aux tests du domaine backend, et un contrôle de mise en page d'un état fixe à un test de composant. Pas de spec de brouillon ou de capture (`zz-*`, `scratch-*`, `*-capture`, sondes `DBG`), pas de `.only`, pas de `it.skip` sans numéro d'issue : `frontend/cypress/specHygiene.test.ts` (jouée par `npm test`) l'impose. Chaque spec crée sa propre salle et ferme ses joueurs STOMP scriptés ; pas de `cy.wait(ms)` fixe sauf une fenêtre d'attente nommée et commentée.

## Environnement de dev

- Pas de base de données, donc pas de jeu de données à charger : l'état vit en mémoire, une partie se crée depuis l'interface (deux onglets ou deux navigateurs pour deux joueurs).
- Seul : `sh dev.sh` (équivalent de `docker compose -f docker-compose.dev.yml up --build`). Frontend http://localhost:5173, backend http://localhost:8080, débogage distant 5005. Le backend a un healthcheck : ajouter `-d --wait` pour l'attendre en arrière-plan.
- Cycle de vie : on ne crée un environnement que pour une story avec une fonctionnalité testable à la main (UI, parcours utilisateur), jamais pour du travail technique ; une fois la PR mergée, la session le ferme (`docker compose -p <projet> down`, sans `-v`) sans demander, après avoir vérifié avec `gh` que la PR est mergée et avec `docker ps` que le projet est bien le sien, et ne ferme jamais celui d'une PR ouverte ou d'une autre session sans accord.
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

  URL : http://localhost:5175. Pour l'e2e sur une pile décalée (même fichier de ports, mais `-f docker-compose.e2e.yml` et un nom de projet propre), Cypress doit viser son port : `npx cypress run --config baseUrl=http://localhost:5175`, ce qui évite d'arrêter la pile de l'autre session.
- Production : derrière le gateway partagé (`C:/Users/kevin/Documents/Git/gateway`, réseau Docker `web`) ; ne jamais lancer `prod.sh` depuis une session de dev.

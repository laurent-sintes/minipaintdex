# Serveur local

1. Vérifier si `127.0.0.1:8080` est occupé. Ne jamais arrêter un processus inconnu.
2. Construire l’état courant avec `scripts/minipaintdex.ps1 build`. Ne pas servir un JAR obsolète après un échec.
3. Lancer `scripts/minipaintdex.ps1 server` dans une session PTY persistante.
4. Attendre le démarrage Spring puis vérifier :
   - `GET /api/v1/health` ;
   - `GET /api/v1/bootstrap?includeMarketPaints=false` ;
   - `GET /` pour la SPA embarquée ;
   - `GET /api/v1/about` lorsque les métadonnées de build sont concernées.
5. Laisser le serveur en fonctionnement et indiquer `http://127.0.0.1:8080/`.

Le serveur Spring Boot self-contained est le défaut. Utiliser Vite sur `5173` uniquement à la demande explicite de hot reload frontend.

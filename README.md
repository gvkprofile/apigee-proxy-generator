# Apigee Proxy Generator

## Usage

```bash
export GITHUB_TOKEN=your_token
mvn spring-boot:run
curl -X POST localhost:8080/api/generate-proxy \
     -d "oasUrl=https://.../openapi.json" \
     -d "repoUrl=https://github.com/you/repo.git"
```

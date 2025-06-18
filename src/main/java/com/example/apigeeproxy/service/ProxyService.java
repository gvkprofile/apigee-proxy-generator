package com.example.apigeeproxy.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

@Service
public class ProxyService {

	@Value("${app.apigee.apiName}")
	private String apiName;

	// Load OAS from classpath or file system
	public JsonNode loadOas(String path) throws IOException {
		ObjectMapper mapper = path.endsWith(".yaml") || path.endsWith(".yml") ? new ObjectMapper(new YAMLFactory())
				: new ObjectMapper();

		Path fsPath = Paths.get(path);
		if (Files.exists(fsPath)) {
			return mapper.readTree(Files.newBufferedReader(fsPath));
		} else {
			ClassPathResource resource = new ClassPathResource(path);
			return mapper.readTree(resource.getInputStream());
		}
	}

	public String generateProxyBundleAndPush(String oasPathStr, String repoUrl, String ghUser, String ghToken)
			throws Exception {
		JsonNode oas = loadOas(oasPathStr);

		String basePath = "/api";
		String targetUrl = "https://example.com";

		if (oas.path("servers").isArray() && oas.path("servers").size() > 0) {
			String url = oas.path("servers").get(0).path("url").asText();
			basePath = url.replaceAll("https?://[^/]+", "");
			targetUrl = url;
		}

		Path tmp = Paths.get("target/apis/" + apiName);
		if (Files.exists(tmp)) {
			// Clean the directory if it exists
			Files.walk(tmp).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
		}
		Files.createDirectories(tmp);
		Path apiproxy = tmp.resolve("apiproxy");
		Files.createDirectories(apiproxy.resolve("proxies"));
		Files.createDirectories(apiproxy.resolve("targets"));
		Files.createDirectories(apiproxy.resolve("policies"));

		// API Proxy Descriptor
		Files.writeString(apiproxy.resolve(apiName + ".xml"),
				"<APIProxy name=\"" + apiName + "\">\n" + "  <Description>Generated from OpenAPI</Description>\n"
						+ "  <ProxyEndpoints><ProxyEndpoint>default</ProxyEndpoint></ProxyEndpoints>\n"
						+ "  <TargetEndpoints><TargetEndpoint>default</TargetEndpoint></TargetEndpoints>\n"
						+ "</APIProxy>");

		// Flows from paths
		StringBuilder flows = new StringBuilder();
		Iterator<Map.Entry<String, JsonNode>> pathIter = oas.path("paths").fields();
		while (pathIter.hasNext()) {
			Map.Entry<String, JsonNode> entry = pathIter.next();
			String path = entry.getKey();
			JsonNode methods = entry.getValue();
			Iterator<String> methodNames = methods.fieldNames();
			while (methodNames.hasNext()) {
				String method = methodNames.next().toUpperCase();
				flows.append("    <Flow name=\"" + method + " " + path + "\">\n")
						.append("      <Request><Step><Name>Verify-API-Key</Name></Step></Request>\n")
						.append("      <Response/>\n")
						.append("      <Condition>(request.verb = \"" + method
								+ "\") and (proxy.pathsuffix MatchesPath \"" + path + "\")</Condition>\n")
						.append("    </Flow>\n");
			}
		}

		Files.writeString(apiproxy.resolve("proxies/default.xml"),
				"<ProxyEndpoint name=\"default\">\n" + "  <PreFlow name=\"PreFlow\"><Request/><Response/></PreFlow>\n"
						+ "  <Flows>\n" + flows + "  </Flows>\n"
						+ "  <PostFlow name=\"PostFlow\"><Request/><Response/></PostFlow>\n"
						+ "  <HTTPProxyConnection>\n" + "    <BasePath>" + basePath + "</BasePath>\n"
						+ "    <VirtualHost>default</VirtualHost>\n" + "  </HTTPProxyConnection>\n"
						+ "  <RouteRule name=\"default\"><TargetEndpoint>default</TargetEndpoint></RouteRule>\n"
						+ "</ProxyEndpoint>");

		Files.writeString(apiproxy.resolve("targets/default.xml"),
				"<TargetEndpoint name=\"default\">\n" + "  <HTTPTargetConnection>\n" + "    <URL>" + targetUrl
						+ "</URL>\n" + "  </HTTPTargetConnection>\n" + "</TargetEndpoint>");

		Files.writeString(apiproxy.resolve("policies/Verify-API-Key.xml"), "<VerifyAPIKey name=\"Verify-API-Key\">\n"
				+ "  <APIKey ref=\"request.queryparam.apikey\"/>\n" + "</VerifyAPIKey>");

		// Push to GitHub
		// 4. Initialize Git
		
		/*
		 * Git git = Git.init().setDirectory(tmp.toFile()).call();
		 * git.add().addFilepattern(".").call();
		 * git.commit().setMessage("Generated proxy from OpenAPI for " +
		 * apiName).call(); git.branchRename() .setOldName("master") .setNewName("main")
		 * .call(); git.remoteAdd().setName("origin").setUri(new
		 * URIish(repoUrl)).call(); git.push() .setCredentialsProvider(new
		 * UsernamePasswordCredentialsProvider(ghUser, ghToken)) .call();
		 */
		Git git = Git.init().setDirectory(tmp.toFile()).call();

		// Add all files
		git.add().addFilepattern(".").call();

		// Commit files
		git.commit()
		   .setMessage("Generated proxy from OpenAPI for " + apiName)
		   .call();

		// Rename 'master' to 'main'
		git.branchRename()
		   .setOldName("master")
		   .setNewName("main")
		   .call();

		// 🔥 Now switch to 'main' branch (important!)
		git.checkout()
		   .setName("main")
		   .call();

		// Add remote
		git.remoteAdd()
		   .setName("origin")
		   .setUri(new URIish(repoUrl))
		   .call();

		// Push to main
		git.push()
		   .setRemote("origin")
		   .add("main") // explicitly push main
		   .setCredentialsProvider(new UsernamePasswordCredentialsProvider(ghUser, ghToken))
		   .call();
		System.out.println("Current branch: " + git.getRepository().getBranch());



		return "Proxy generated and pushed to " + repoUrl;
	}

	public String generateSpringClasses(String oasPathStr, String outputDirStr) throws IOException {
		JsonNode root = loadOas(oasPathStr);
		JsonNode paths = root.path("paths");

		Path outputDir = Paths.get(outputDirStr);

		StringBuilder controller = new StringBuilder();
		StringBuilder service = new StringBuilder();

		controller.append("package com.example.generated;\n\n")
				.append("import org.springframework.web.bind.annotation.*;\n")
				.append("import org.springframework.beans.factory.annotation.Autowired;\n")
				.append("import org.springframework.http.ResponseEntity;\n")
				.append("@RestController\n@RequestMapping(\"/api\")\n").append("public class GeneratedController {\n\n")
				.append("    @Autowired\n    private GeneratedService service;\n\n");

		service.append("package com.example.generated;\n\n").append("import org.springframework.stereotype.Service;\n")
				.append("@Service\npublic class GeneratedService {\n\n");

		Iterator<Map.Entry<String, JsonNode>> pathIter = paths.fields();
		while (pathIter.hasNext()) {
			Map.Entry<String, JsonNode> entry = pathIter.next();
			String path = entry.getKey();
			JsonNode methods = entry.getValue();

			Iterator<String> methodNames = methods.fieldNames();
			while (methodNames.hasNext()) {
				String method = methodNames.next();
				String methodName = method.toUpperCase() + path.replaceAll("[/{}]", "_");

				controller.append("    @").append(method.toUpperCase()).append("Mapping(\"").append(path)
						.append("\")\n").append("    public ResponseEntity<String> ").append(methodName)
						.append("() {\n").append("        return ResponseEntity.ok(service.").append(methodName)
						.append("());\n").append("    }\n\n");

				service.append("    public String ").append(methodName).append("() {\n")
						.append("        return \"Handled ").append(method.toUpperCase()).append(" ").append(path)
						.append("\";\n").append("    }\n\n");
			}
		}

		controller.append("}");
		service.append("}");

		Path pkgDir = outputDir.resolve("com/example/generated");
		Files.createDirectories(pkgDir);
		Files.writeString(pkgDir.resolve("GeneratedController.java"), controller.toString());
		Files.writeString(pkgDir.resolve("GeneratedService.java"), service.toString());

		return "Spring classes generated at: " + pkgDir.toAbsolutePath();
	}
}

package managerAgent.controller;

import data.PromptSchema;
import data.ResponseSchema;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import managerAgent.agents.ManagerAgent;
import managerAgent.util.PlanExporter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;

@RestController
public class ManagerAgentController {
    @Resource
    private ManagerAgent managerAgent;

    @Value("${AMAP_SECURITY_JS_CODE:}")
    private String amapSecurityCode;

    private final HttpClient amapHttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @RequestMapping(value = {"/_AMapService/**", "/_AMapService"}, method = RequestMethod.GET)
    public void amapServiceProxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (amapSecurityCode == null || amapSecurityCode.isBlank()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "AMap service proxy is not configured");
            return;
        }
        String prefix = request.getContextPath() + "/_AMapService";
        String path = request.getRequestURI().substring(prefix.length());
        if (path.isBlank()) path = "/";
        if (!path.matches("/[A-Za-z0-9_./-]*") || path.contains("..")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        boolean styleRequest = path.startsWith("/v4/map/styles");
        String host = styleRequest ? "https://webapi.amap.com" : "https://restapi.amap.com";
        String query = request.getQueryString();
        String jscode = "jscode=" + URLEncoder.encode(amapSecurityCode, StandardCharsets.UTF_8);
        URI uri = URI.create(host + path + (query == null || query.isBlank() ? "?" : "?" + query + "&") + jscode);
        try {
            HttpRequest upstream = HttpRequest.newBuilder(uri).GET().build();
            var result = amapHttpClient.send(upstream, BodyHandlers.ofByteArray());
            response.setStatus(result.statusCode());
            response.setContentType(result.headers().firstValue("content-type").orElse("application/json;charset=UTF-8"));
            response.getOutputStream().write(result.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY);
        }
    }

    @RequestMapping(
            value = "/trip",
            produces = "application/json;charset=UTF-8",
            method = RequestMethod.POST)
    public ResponseSchema tripPlan(@RequestBody PromptSchema input) {
        if ("__ping__".equals(input.getPrompt())) {
            ResponseSchema pong = new ResponseSchema();
            pong.response = "pong";
            return pong;
        }
        return managerAgent.run(input.getPrompt());
    }

    @RequestMapping(
            value = "/trip/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE,
            method = RequestMethod.POST)
    public Flux<String> tripPlanStream(@RequestBody PromptSchema input) {
        if ("__ping__".equals(input.getPrompt())) {
            return Flux.just("{\"type\":\"DONE\",\"text\":\"pong\",\"isLast\":true}");
        }
        return managerAgent.stream(input);
    }

    /**
     * 导出旅行计划为 CSV 文件下载。
     * POST body: { "prompt": "旅行计划markdown全文" }
     */
    @RequestMapping(
            value = "/trip/export",
            method = RequestMethod.POST)
    public void exportPlan(@RequestBody PromptSchema input, HttpServletResponse response) throws IOException {
        String planText = input.getPrompt();
        Path csvFile = PlanExporter.exportToCsv(planText);

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=trip_plan.csv");
        response.setCharacterEncoding("UTF-8");

        try (OutputStream out = response.getOutputStream()) {
            Files.copy(csvFile, out);
            out.flush();
        } finally {
            Files.deleteIfExists(csvFile);
        }
    }
}

package managerAgent.controller;

import data.PromptSchema;
import data.ResponseSchema;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
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

@RestController
public class ManagerAgentController {
    @Resource
    private ManagerAgent managerAgent;

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
        return managerAgent.stream(input.getPrompt());
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

package com.axreng.backend;

import com.axreng.backend.model.CrawlJob;
import com.axreng.backend.service.CrawlManager;
import com.google.gson.Gson;
import spark.Spark;

import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors; // NOVO

import static spark.Spark.*;

public class Main {

    private static final Gson GSON = new Gson();

    // NOVO: Pool de Threads Dedicado para I/O
    // Configura um pool de 10 threads para o HttpClient. Ideal para I/O assíncrono.
    private static final ExecutorService HTTP_EXECUTOR = Executors.newFixedThreadPool(10);

    // CORREÇÃO CRÍTICA DE PERFORMANCE:
    // O HttpClient é criado UMA VEZ e usa o pool de I/O otimizado.
    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
            .version(Version.HTTP_2)
            .followRedirects(Redirect.NORMAL)
            .executor(HTTP_EXECUTOR) // << CORREÇÃO: Injeta o pool de I/O
            .build();

    public static void main(String[] args) {
        // Requisito 4: Lê a URL base da variável de ambiente
        final String baseURL = System.getenv("BASE_URL");
        if (baseURL == null || baseURL.isEmpty()) {
            System.err.println("Erro: Variável de ambiente BASE_URL não configurada. Use -e BASE_URL=...");
            System.exit(1);
        }

        // Passa o cliente compartilhado para o manager
        final CrawlManager manager = new CrawlManager(baseURL, SHARED_HTTP_CLIENT);

        // Requisito 1: API HTTP na porta 4567
        port(4567);

        // Define o endpoint POST /crawl
        post("/crawl", (request, response) -> {
            response.type("application/json");

            // ... (restante do código post)
            @SuppressWarnings("unchecked")
            Map<String, String> body = GSON.fromJson(request.body(), Map.class);
            String keyword = body.get("keyword");

            if (keyword == null || keyword.length() < 4 || keyword.length() > 32) {
                response.status(400); // Bad Request
                return GSON.toJson(Collections.singletonMap("error",
                        "O termo buscado deve ter no mínimo 4 e no máximo 32 caracteres."));
            }

            CrawlJob job = manager.startNewCrawl(keyword);

            response.status(200); // 200 OK
            return GSON.toJson(Collections.singletonMap("id", job.getId()));
        });

        // Define o endpoint GET /crawl/:id
        get("/crawl/:id", (request, response) -> {
            response.type("application/json");
            String id = request.params(":id");

            CrawlJob job = manager.getCrawlResult(id);

            if (job == null) {
                response.status(404); // Not Found
                return GSON.toJson(Collections.singletonMap("error", "Busca não encontrada."));
            }

            response.status(200); // 200 OK
            return GSON.toJson(job);
        });

        // Espera o Spark iniciar
        Spark.awaitInitialization();
        System.out.println("API Server rodando na porta 4567. BASE_URL: " + baseURL);
    }
}

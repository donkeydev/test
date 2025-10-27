package com.axreng.backend.service;

import com.axreng.backend.model.CrawlJob;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebCrawler {

    // Regex para buscar links no formato <a href="...">
    // É uma regex simplificada e não robusta como um parser DOM, mas suficiente para o teste.
    private static final Pattern LINK_PATTERN = Pattern.compile("<a\\s+[^>]*?href=\"(.*?)\"", Pattern.CASE_INSENSITIVE);

    private final String baseUrl;
    // CORREÇÃO: Cliente HTTP agora é injetado, não inicializado aqui.
    private final HttpClient httpClient;

    // Para controlar as URLs já visitadas de forma thread-safe
    private final Set<String> visitedUrls;

    // Pattern para a keyword, criado no construtor do job para eficiência
    private Pattern keywordPattern;

    // CORREÇÃO: Recebe o cliente HTTP compartilhado.
    public WebCrawler(String baseUrl, HttpClient sharedHttpClient) {
        // Remove barra final se existir (Requisito 4)
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        // CORREÇÃO: Armazena o cliente HTTP compartilhado, eliminando a latência de inicialização.
        this.httpClient = sharedHttpClient;

        this.visitedUrls = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    public void startCrawl(CrawlJob job) {
        // Compila o pattern da keyword (Requisito 2: case-insensitive)
        this.keywordPattern = Pattern.compile(Pattern.quote(job.getKeyword()), Pattern.CASE_INSENSITIVE);

        // Inicia a navegação de forma assíncrona
        crawlAsync(baseUrl, job);
    }

    // Método principal que usa CompletableFuture para processar a requisição de forma não-bloqueante
    private void crawlAsync(String currentUrl, CrawlJob job) {
        // Requisito 4: Verifica escopo e evita repetição
        if (!currentUrl.startsWith(baseUrl) || visitedUrls.contains(currentUrl)) {
            return;
        }

        visitedUrls.add(currentUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(currentUrl))
                .header("User-Agent", "AxurCrawler/1.0")
                .GET()
                .build();

        // Envia a requisição assíncrona
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    // Se o status for OK (2xx)
                    if (response.statusCode() / 100 == 2) {
                        processPageContent(response.body(), currentUrl, job);
                    }
                })
                // Continua a navegação mesmo se a requisição falhar
                .exceptionally(ex -> {
                    System.err.println("Erro ao buscar URL " + currentUrl + ": " + ex.getMessage());
                    return null;
                });
    }

    private void processPageContent(String htmlContent, String currentUrl, CrawlJob job) {
        // 1. Busca da Keyword (Requisito 2: Case Insensitive)
        Matcher keywordMatcher = keywordPattern.matcher(htmlContent);
        if (keywordMatcher.find()) {
            job.addUrl(currentUrl); // Adiciona URL aos resultados parciais (Requisito 6)
        }

        // 2. Extração de Links
        Matcher linkMatcher = LINK_PATTERN.matcher(htmlContent);
        while (linkMatcher.find()) {
            String link = linkMatcher.group(1);

            // Resolve links relativos. Não é perfeito, mas é o máximo que podemos fazer sem bibliotecas.
            String absUrl = resolveRelativeUrl(currentUrl, link);

            // Requisito 4: Seguir links no escopo
            if (absUrl.startsWith(baseUrl) && !visitedUrls.contains(absUrl)) {
                // Chama a navegação para o novo link de forma assíncrona (recursão não-bloqueante)
                crawlAsync(absUrl, job);
            }
        }
    }

    // Lógica simplificada para resolver URLs relativas (sem bibliotecas)
    private String resolveRelativeUrl(String baseUrl, String relativeUrl) {
        if (relativeUrl.startsWith("http") || relativeUrl.startsWith("https")) {
            return relativeUrl;
        }
        if (relativeUrl.startsWith("/")) {
            // Se for /path/to/page, adiciona ao root
            return baseUrl + relativeUrl;
        }
        // Tratar outros casos (./path, ../path, path/to/page) é complexo sem libs.
        // Assumimos links simples, ou o crawler não será 100% eficaz.
        String path = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);
        return path + relativeUrl;
    }
}

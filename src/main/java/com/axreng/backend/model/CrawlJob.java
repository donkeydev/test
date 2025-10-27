package com.axreng.backend.model;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CrawlJob {
    // 1. ABRORDAGEM FINAL: Removemos o Enum para evitar a serialização indesejada do GSON.
    // O status será armazenado diretamente como uma String minúscula.

    private final String id;
    private final String keyword;

    // 2. O campo 'status' agora é uma String.
    // Usamos 'status' como o nome do campo para que o GSON o encontre diretamente.
    private volatile String status;

    // O campo de URLs para resultados (já corrigido para 'urls')
    private final Set<String> urls;

    public CrawlJob(String id, String keyword) {
        this.id = id;
        this.keyword = keyword;
        // 3. Inicializamos o status diretamente como a string minúscula "active".
        this.status = "active";
        this.urls = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    public String getId() { return id; }
    public String getKeyword() { return keyword; }

    // 4. O getter agora retorna a String minúscula diretamente.
    public String getStatus() { return status; }

    public Set<String> getUrls() { return urls; }

    // 5. O método de conclusão define o status como a string minúscula "done".
    public void setStatusDone() { this.status = "done"; }
    public void addUrl(String url) { urls.add(url); }
}
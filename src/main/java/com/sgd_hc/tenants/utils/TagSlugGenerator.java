package com.sgd_hc.tenants.utils;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TagSlugGenerator {

    private static final int MIN_WORD_LENGTH = 3;
    private static final int SINGLE_WORD_MAX_LENGTH = 8;
    private static final int SINGLE_WORD_TRUNCATE_LENGTH = 4;
    private static final int MIN_TAGSLUG_LENGTH = 2;
    private static final int MAX_TAGSLUG_LENGTH = 8;

    /**
     * Genera un tagslug base a partir del nombre de un tenant.
     */
    public static String generateTenantSlug(String name) {
        if (name == null || name.isBlank()) {
            return "tg"; // Fallback mínimo
        }

        // 1. Normalizar: minúsculas, sin acentos, sin símbolos y sin espacios múltiples
        String normalized = normalize(name);
        
        // 2. Dividir en palabras y filtrar palabras con longitud >= MIN_WORD_LENGTH
        List<String> validWords = Arrays.stream(normalized.split("\\s+"))
                .filter(w -> w.length() >= MIN_WORD_LENGTH)
                .collect(Collectors.toList());

        String tagSlug;

        // 3. Estrategia según el número de palabras válidas
        if (validWords.size() >= 2) {
            // Regla: Usar las iniciales de cada palabra válida
            tagSlug = validWords.stream()
                    .map(w -> String.valueOf(w.charAt(0)))
                    .collect(Collectors.joining());
        } else if (validWords.size() == 1) {
            // Regla: Palabra única
            String word = validWords.get(0);
            if (word.length() < SINGLE_WORD_MAX_LENGTH) {
                tagSlug = word; // Palabra completa si es corta
            } else {
                tagSlug = word.substring(0, SINGLE_WORD_TRUNCATE_LENGTH); // Primeros caracteres si es larga
            }
        } else {
            // FALLBACK: Si no quedan palabras largas, usar iniciales de palabras originales
            List<String> allWords = Arrays.stream(normalized.split("\\s+"))
                    .filter(w -> !w.isBlank())
                    .collect(Collectors.toList());
            
            if (allWords.isEmpty()) {
                tagSlug = "tg";
            } else {
                tagSlug = allWords.stream()
                        .map(w -> String.valueOf(w.charAt(0)))
                        .collect(Collectors.joining());
            }
        }

        // 4. Limpieza final y validación de longitud
        tagSlug = tagSlug.toLowerCase().replaceAll("[^a-z0-9]", "");
        
        if (tagSlug.length() < MIN_TAGSLUG_LENGTH) {
            // Asegurar mínimo de caracteres
            tagSlug = (tagSlug.length() == 1) ? tagSlug + "1" : "tg";
        }
        
        if (tagSlug.length() > MAX_TAGSLUG_LENGTH) {
            // Limitar longitud máxima
            tagSlug = tagSlug.substring(0, MAX_TAGSLUG_LENGTH);
        }

        return tagSlug;
    }

    private static String normalize(String input) {
        if (input == null) return "";
        
        // Eliminar acentos
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        
        return normalized.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}

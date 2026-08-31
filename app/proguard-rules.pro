# Regras de release. Vazio por ora — entra conteúdo quando houver
# reflexão real (Room e Hilt já trazem as suas próprias).

# Art. 15 — nenhum valor monetário ou descrição em log, nem em release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

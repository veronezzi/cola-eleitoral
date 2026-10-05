# Regras R8 do app.
#
# Retrofit, OkHttp, kotlinx.serialization, Hilt/Dagger, Room, WorkManager, DataStore, Coil e
# Compose já publicam as próprias regras de consumidor dentro dos artefatos, então nada deles
# precisa ser repetido aqui. Só acrescente regras com motivo concreto (e comentado), por exemplo
# uma classe acessada por reflexão que as regras das bibliotecas não cobrem.

# Mantém número de linha nos stack traces; o mapping.txt vai no bundle e o Play Console
# desofusca os crashes (o app não usa SDK de crash reporting).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Defesa extra (ARCHITECTURE.md 5.5): o R8 apaga as chamadas a Log.v, Log.d e Log.i do build de
# release, inclusive as de bibliotecas. O código do app não registra nada, e nunca registra escolhas
# em nenhum build; Log.w e Log.e ficam para avisos de bibliotecas, que não carregam dados do usuário.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

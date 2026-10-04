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

.class final Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;
.super Ljava/lang/Object;
.implements Ljava/lang/Runnable;
.source "CentralBrainPanelController.java"

.field private final controller:Lcom/tuanjie/urasclient2/CentralBrainPanelController;

.field private final userText:Ljava/lang/String;


.method public constructor <init>(Lcom/tuanjie/urasclient2/CentralBrainPanelController;Ljava/lang/String;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->controller:Lcom/tuanjie/urasclient2/CentralBrainPanelController;

    iput-object p2, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->userText:Ljava/lang/String;

    return-void
.end method

.method private buildPayload()Ljava/lang/String;
    .locals 7

    new-instance v0, Lorg/json/JSONObject;

    invoke-direct {v0}, Lorg/json/JSONObject;-><init>()V

    const-string v1, "runtime"

    const-string v2, "ollama"

    invoke-virtual {v0, v1, v2}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    const-string v1, "model"

    const-string v2, "central-intent-v0"

    invoke-virtual {v0, v1, v2}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    new-instance v3, Lorg/json/JSONObject;

    invoke-direct {v3}, Lorg/json/JSONObject;-><init>()V

    const-string v1, "utterance"

    iget-object v2, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->userText:Ljava/lang/String;

    invoke-virtual {v3, v1, v2}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    const-string v1, "source"

    const-string v2, "client2-right-panel"

    invoke-virtual {v3, v1, v2}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    const-string v1, "locale"

    const-string v2, "zh-CN"

    invoke-virtual {v3, v1, v2}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    const-string v1, "input"

    invoke-virtual {v0, v1, v3}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    new-instance v4, Lorg/json/JSONObject;

    invoke-direct {v4}, Lorg/json/JSONObject;-><init>()V

    const-string v1, "safety_state_required"

    const-string v2, "normal"

    invoke-virtual {v4, v1, v2}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    const-string v1, "timeout_ms"

    const v5, 0x1d4c0

    invoke-virtual {v4, v1, v5}, Lorg/json/JSONObject;->put(Ljava/lang/String;I)Lorg/json/JSONObject;

    const-string v1, "policy"

    invoke-virtual {v0, v1, v4}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    const-string v1, "caller"

    const-string v6, "Client2"

    invoke-virtual {v0, v1, v6}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    invoke-virtual {v0}, Lorg/json/JSONObject;->toString()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method

.method private extractReply(Ljava/lang/String;)Ljava/lang/String;
    .locals 7

    if-eqz p1, :cond_empty

    invoke-direct {p0, p1}, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->hasText(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_empty

    const-string v6, ""

    :try_start_0
    new-instance v0, Lorg/json/JSONObject;

    invoke-direct {v0, p1}, Lorg/json/JSONObject;-><init>(Ljava/lang/String;)V

    const-string v1, "result"

    invoke-virtual {v0, v1}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    move-result-object v2

    if-eqz v2, :cond_root

    const-string v1, "generated_text"

    invoke-virtual {v2, v1}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    invoke-direct {p0, v3}, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->hasText(Ljava/lang/String;)Z

    move-result v4

    if-eqz v4, :cond_summary

    move-object v6, v3

    goto :cond_done

    :cond_summary
    const-string v1, "summary"

    invoke-virtual {v2, v1}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    invoke-direct {p0, v3}, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->hasText(Ljava/lang/String;)Z

    move-result v4

    if-eqz v4, :cond_reason

    move-object v6, v3

    goto :cond_done

    :cond_reason
    const-string v1, "reason"

    invoke-virtual {v2, v1}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    invoke-direct {p0, v3}, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->hasText(Ljava/lang/String;)Z

    move-result v4

    if-eqz v4, :cond_root

    move-object v6, v3

    goto :cond_done

    :cond_root
    const-string v1, "response"

    invoke-virtual {v0, v1}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    invoke-direct {p0, v3}, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->hasText(Ljava/lang/String;)Z

    move-result v4

    if-eqz v4, :cond_text

    move-object v6, v3

    goto :cond_done

    :cond_text
    const-string v1, "text"

    invoke-virtual {v0, v1}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    invoke-direct {p0, v3}, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->hasText(Ljava/lang/String;)Z

    move-result v4

    if-eqz v4, :cond_done

    move-object v6, v3

    :cond_done
    invoke-direct {p0, v6}, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->hasText(Ljava/lang/String;)Z

    move-result v5
    :try_end_0
    .catch Lorg/json/JSONException; {:try_start_0 .. :try_end_0} :catch_0

    if-eqz v5, :cond_raw

    return-object v6

    :cond_raw
    return-object p1

    :catch_0
    move-exception v0

    return-object p1

    :cond_empty
    const-string v0, "\u6a21\u578b\u6ca1\u6709\u8fd4\u56de\u5185\u5bb9"

    return-object v0
.end method

.method private hasText(Ljava/lang/String;)Z
    .locals 2

    if-eqz p1, :cond_false

    invoke-virtual {p1}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/String;->length()I

    move-result v1

    if-lez v1, :cond_false

    const/4 v0, 0x1

    return v0

    :cond_false
    const/4 v0, 0x0

    return v0
.end method

.method private readStream(Ljava/io/InputStream;)Ljava/lang/String;
    .locals 4

    new-instance v0, Ljava/util/Scanner;

    const-string v1, "UTF-8"

    invoke-direct {v0, p1, v1}, Ljava/util/Scanner;-><init>(Ljava/io/InputStream;Ljava/lang/String;)V

    const-string v1, "\\A"

    invoke-virtual {v0, v1}, Ljava/util/Scanner;->useDelimiter(Ljava/lang/String;)Ljava/util/Scanner;

    invoke-virtual {v0}, Ljava/util/Scanner;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_empty

    invoke-virtual {v0}, Ljava/util/Scanner;->next()Ljava/lang/String;

    move-result-object v2

    goto :cond_done

    :cond_empty
    const-string v2, ""

    :cond_done
    invoke-virtual {v0}, Ljava/util/Scanner;->close()V

    return-object v2
.end method

.method private requestModel()Ljava/lang/String;
    .locals 9

    invoke-direct {p0}, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->buildPayload()Ljava/lang/String;

    move-result-object v0

    new-instance v1, Ljava/net/URL;

    const-string v2, "http://10.0.2.2:8787/ai/infer"

    invoke-direct {v1, v2}, Ljava/net/URL;-><init>(Ljava/lang/String;)V

    invoke-virtual {v1}, Ljava/net/URL;->openConnection()Ljava/net/URLConnection;

    move-result-object v2

    check-cast v2, Ljava/net/HttpURLConnection;

    const-string v3, "POST"

    invoke-virtual {v2, v3}, Ljava/net/HttpURLConnection;->setRequestMethod(Ljava/lang/String;)V

    const/16 v3, 0x1388

    invoke-virtual {v2, v3}, Ljava/net/HttpURLConnection;->setConnectTimeout(I)V

    const v3, 0x1d4c0

    invoke-virtual {v2, v3}, Ljava/net/HttpURLConnection;->setReadTimeout(I)V

    const/4 v3, 0x1

    invoke-virtual {v2, v3}, Ljava/net/HttpURLConnection;->setDoOutput(Z)V

    const-string v3, "Content-Type"

    const-string v4, "application/json; charset=utf-8"

    invoke-virtual {v2, v3, v4}, Ljava/net/HttpURLConnection;->setRequestProperty(Ljava/lang/String;Ljava/lang/String;)V

    const-string v3, "Accept"

    const-string v4, "application/json"

    invoke-virtual {v2, v3, v4}, Ljava/net/HttpURLConnection;->setRequestProperty(Ljava/lang/String;Ljava/lang/String;)V

    invoke-virtual {v2}, Ljava/net/HttpURLConnection;->getOutputStream()Ljava/io/OutputStream;

    move-result-object v3

    const-string v4, "UTF-8"

    invoke-virtual {v0, v4}, Ljava/lang/String;->getBytes(Ljava/lang/String;)[B

    move-result-object v4

    invoke-virtual {v3, v4}, Ljava/io/OutputStream;->write([B)V

    invoke-virtual {v3}, Ljava/io/OutputStream;->close()V

    invoke-virtual {v2}, Ljava/net/HttpURLConnection;->getResponseCode()I

    move-result v5

    const/16 v6, 0x190

    if-lt v5, v6, :cond_ok

    invoke-virtual {v2}, Ljava/net/HttpURLConnection;->getErrorStream()Ljava/io/InputStream;

    move-result-object v6

    goto :cond_stream

    :cond_ok
    invoke-virtual {v2}, Ljava/net/HttpURLConnection;->getInputStream()Ljava/io/InputStream;

    move-result-object v6

    :cond_stream
    if-eqz v6, :cond_empty

    invoke-direct {p0, v6}, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->readStream(Ljava/io/InputStream;)Ljava/lang/String;

    move-result-object v7

    invoke-direct {p0, v7}, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->extractReply(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v8

    invoke-virtual {v2}, Ljava/net/HttpURLConnection;->disconnect()V

    return-object v8

    :cond_empty
    invoke-virtual {v2}, Ljava/net/HttpURLConnection;->disconnect()V

    const-string v8, "\u6a21\u578b\u6ca1\u6709\u8fd4\u56de\u5185\u5bb9"

    return-object v8
.end method

.method public run()V
    .locals 4

    :try_start_0
    invoke-direct {p0}, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->requestModel()Ljava/lang/String;

    move-result-object v0

    iget-object v1, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->controller:Lcom/tuanjie/urasclient2/CentralBrainPanelController;

    invoke-virtual {v1, v0}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->showReply(Ljava/lang/String;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception v0

    invoke-virtual {v0}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;

    move-result-object v1

    if-nez v1, :cond_msg

    invoke-virtual {v0}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object v1

    :cond_msg
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "\u8bf7\u6c42\u5931\u8d25: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    iget-object v3, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;->controller:Lcom/tuanjie/urasclient2/CentralBrainPanelController;

    invoke-virtual {v3, v2}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->showReply(Ljava/lang/String;)V

    return-void
.end method

.class public Lcom/tuanjie/urasclient2/CentralBrainPanelController;
.super Ljava/lang/Object;
.implements Landroid/view/View$OnClickListener;
.source "CentralBrainPanelController.java"

.field private final activity:Landroid/app/Activity;

.field private replyView:Landroid/widget/TextView;

.field private static requestInFlight:Z


.method public constructor <init>(Landroid/app/Activity;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->activity:Landroid/app/Activity;

    return-void
.end method

.method public static install(Landroid/app/Activity;)V
    .locals 1

    if-eqz p0, :cond_0

    new-instance v0, Lcom/tuanjie/urasclient2/CentralBrainPanelController;

    invoke-direct {v0, p0}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;-><init>(Landroid/app/Activity;)V

    invoke-direct {v0}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->bind()V

    :cond_0
    return-void
.end method

.method private bind()V
    .locals 10

    iget-object v0, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->activity:Landroid/app/Activity;

    invoke-virtual {v0}, Landroid/app/Activity;->getResources()Landroid/content/res/Resources;

    move-result-object v1

    invoke-virtual {v0}, Landroid/app/Activity;->getPackageName()Ljava/lang/String;

    move-result-object v2

    const-string v3, "id"

    const-string v4, "centralBrainColdButton"

    invoke-virtual {v1, v4, v3, v2}, Landroid/content/res/Resources;->getIdentifier(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I

    move-result v4

    invoke-virtual {v0, v4}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;

    move-result-object v4

    instance-of v5, v4, Landroid/widget/Button;

    if-eqz v5, :cond_0

    check-cast v4, Landroid/widget/Button;

    const/4 v5, 0x0

    invoke-virtual {v4, v5}, Landroid/widget/Button;->setBackgroundTintList(Landroid/content/res/ColorStateList;)V

    invoke-virtual {v4, p0}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    :cond_0
    const-string v6, "centralBrainTiredButton"

    invoke-virtual {v1, v6, v3, v2}, Landroid/content/res/Resources;->getIdentifier(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I

    move-result v6

    invoke-virtual {v0, v6}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;

    move-result-object v6

    instance-of v7, v6, Landroid/widget/Button;

    if-eqz v7, :cond_1

    check-cast v6, Landroid/widget/Button;

    const/4 v7, 0x0

    invoke-virtual {v6, v7}, Landroid/widget/Button;->setBackgroundTintList(Landroid/content/res/ColorStateList;)V

    invoke-virtual {v6, p0}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    :cond_1
    const-string v8, "centralBrainReplyText"

    invoke-virtual {v1, v8, v3, v2}, Landroid/content/res/Resources;->getIdentifier(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I

    move-result v8

    invoke-virtual {v0, v8}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;

    move-result-object v8

    instance-of v9, v8, Landroid/widget/TextView;

    if-eqz v9, :cond_2

    check-cast v8, Landroid/widget/TextView;

    iput-object v8, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->replyView:Landroid/widget/TextView;

    const-string v9, "\u7b49\u5f85\u6307\u4ee4..."

    invoke-virtual {v8, v9}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    :cond_2
    return-void
.end method

.method public onClick(Landroid/view/View;)V
    .locals 2

    instance-of v0, p1, Landroid/widget/TextView;

    if-eqz v0, :cond_1

    check-cast p1, Landroid/widget/TextView;

    invoke-virtual {p1}, Landroid/widget/TextView;->getText()Ljava/lang/CharSequence;

    move-result-object v0

    if-eqz v0, :cond_0

    invoke-interface {v0}, Ljava/lang/CharSequence;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-direct {p0, v1}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->submitIntent(Ljava/lang/String;)V

    return-void

    :cond_0
    const-string v1, ""

    invoke-direct {p0, v1}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->submitIntent(Ljava/lang/String;)V

    :cond_1
    return-void
.end method

.method private submitIntent(Ljava/lang/String;)V
    .locals 4

    if-eqz p1, :cond_0

    sget-boolean v0, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->requestInFlight:Z

    if-nez v0, :cond_0

    const/4 v0, 0x1

    sput-boolean v0, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->requestInFlight:Z

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "\u8bf7\u6c42\u4e2d: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p0, v0}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->setReplyNow(Ljava/lang/String;)V

    new-instance v2, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;

    invoke-direct {v2, p0, p1}, Lcom/tuanjie/urasclient2/CentralBrainPanelController$RequestTask;-><init>(Lcom/tuanjie/urasclient2/CentralBrainPanelController;Ljava/lang/String;)V

    new-instance v3, Ljava/lang/Thread;

    invoke-direct {v3, v2}, Ljava/lang/Thread;-><init>(Ljava/lang/Runnable;)V

    invoke-virtual {v3}, Ljava/lang/Thread;->start()V

    :cond_0
    return-void
.end method

.method public completeRequest()V
    .locals 1

    const/4 v0, 0x0

    sput-boolean v0, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->requestInFlight:Z

    return-void
.end method

.method public showReply(Ljava/lang/String;)V
    .locals 3

    iget-object v0, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->activity:Landroid/app/Activity;

    if-eqz v0, :cond_0

    new-instance v1, Lcom/tuanjie/urasclient2/CentralBrainPanelController$UiUpdate;

    invoke-direct {v1, p0, p1}, Lcom/tuanjie/urasclient2/CentralBrainPanelController$UiUpdate;-><init>(Lcom/tuanjie/urasclient2/CentralBrainPanelController;Ljava/lang/String;)V

    invoke-virtual {v0, v1}, Landroid/app/Activity;->runOnUiThread(Ljava/lang/Runnable;)V

    :cond_0
    return-void
.end method

.method public setReplyNow(Ljava/lang/String;)V
    .locals 1

    iget-object v0, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->replyView:Landroid/widget/TextView;

    if-eqz v0, :cond_1

    if-nez p1, :cond_0

    const-string p1, ""

    :cond_0
    invoke-virtual {v0, p1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    :cond_1
    return-void
.end method

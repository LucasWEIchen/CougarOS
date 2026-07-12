.class public Lcom/tuanjie/urasclient2/CentralBrainPanelController;
.super Ljava/lang/Object;
.implements Landroid/view/View$OnClickListener;
.implements Lcom/centralbrain/client2/ScenarioCallback;
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
    .locals 8

    iget-object v0, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->activity:Landroid/app/Activity;

    invoke-virtual {v0}, Landroid/app/Activity;->getResources()Landroid/content/res/Resources;

    move-result-object v1

    invoke-virtual {v0}, Landroid/app/Activity;->getPackageName()Ljava/lang/String;

    move-result-object v2

    const-string v3, "id"

    const-string v4, "centralBrainControlGroup"

    invoke-virtual {v1, v4, v3, v2}, Landroid/content/res/Resources;->getIdentifier(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I

    move-result v4

    invoke-virtual {v0, v4}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;

    move-result-object v4

    if-eqz v4, :cond_0

    invoke-direct {p0, v4}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->bindButtons(Landroid/view/View;)V

    :cond_0
    const-string v5, "centralBrainReplyText"

    invoke-virtual {v1, v5, v3, v2}, Landroid/content/res/Resources;->getIdentifier(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I

    move-result v5

    invoke-virtual {v0, v5}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;

    move-result-object v5

    instance-of v6, v5, Landroid/widget/TextView;

    if-eqz v6, :cond_1

    check-cast v5, Landroid/widget/TextView;

    iput-object v5, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->replyView:Landroid/widget/TextView;

    const-string v7, "\u9009\u62e9\u6d4b\u8bd5\u573a\u666f..."

    invoke-virtual {v5, v7}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    :cond_1
    return-void
.end method

.method private bindButtons(Landroid/view/View;)V
    .locals 4

    if-eqz p1, :cond_return

    instance-of v0, p1, Landroid/widget/Button;

    if-eqz v0, :cond_group

    check-cast p1, Landroid/widget/Button;

    const/4 v1, 0x0

    invoke-virtual {p1, v1}, Landroid/widget/Button;->setBackgroundTintList(Landroid/content/res/ColorStateList;)V

    invoke-virtual {p1, p0}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    return-void

    :cond_group
    instance-of v0, p1, Landroid/view/ViewGroup;

    if-eqz v0, :cond_return

    check-cast p1, Landroid/view/ViewGroup;

    invoke-virtual {p1}, Landroid/view/ViewGroup;->getChildCount()I

    move-result v1

    const/4 v2, 0x0

    :goto_children
    if-ge v2, v1, :cond_return

    invoke-virtual {p1, v2}, Landroid/view/ViewGroup;->getChildAt(I)Landroid/view/View;

    move-result-object v3

    invoke-direct {p0, v3}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->bindButtons(Landroid/view/View;)V

    add-int/lit8 v2, v2, 0x1

    goto :goto_children

    :cond_return
    return-void
.end method

.method public onClick(Landroid/view/View;)V
    .locals 4

    instance-of v0, p1, Landroid/widget/TextView;

    if-eqz v0, :cond_return

    check-cast p1, Landroid/widget/TextView;

    invoke-virtual {p1}, Landroid/widget/TextView;->getText()Ljava/lang/CharSequence;

    move-result-object v0

    if-eqz v0, :cond_empty

    invoke-interface {v0}, Ljava/lang/CharSequence;->toString()Ljava/lang/String;

    move-result-object v1

    goto :cond_tag

    :cond_empty
    const-string v1, ""

    :cond_tag
    invoke-virtual {p1}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v2

    if-eqz v2, :cond_use_text

    invoke-virtual {v2}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object v3

    goto :cond_submit

    :cond_use_text
    move-object v3, v1

    :cond_submit
    invoke-direct {p0, v3, v1}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->submitScenario(Ljava/lang/String;Ljava/lang/String;)V

    :cond_return
    return-void
.end method

.method private submitScenario(Ljava/lang/String;Ljava/lang/String;)V
    .locals 3

    if-eqz p1, :cond_0

    if-eqz p2, :cond_0

    sget-boolean v0, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->requestInFlight:Z

    if-nez v0, :cond_0

    const/4 v0, 0x1

    sput-boolean v0, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->requestInFlight:Z

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "\u8bf7\u6c42\u4e2d: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p0, v0}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->setReplyNow(Ljava/lang/String;)V

    iget-object v1, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->activity:Landroid/app/Activity;

    invoke-static {v1, p1, p2, p0}, Lcom/centralbrain/client2/Client2ScenarioBridge;->submit(Landroid/app/Activity;Ljava/lang/String;Ljava/lang/String;Lcom/centralbrain/client2/ScenarioCallback;)Z

    move-result v2

    if-nez v2, :cond_0

    invoke-virtual {p0}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->completeRequest()V

    :cond_0
    return-void
.end method

.method public onBridgeStatus(Ljava/lang/String;)V
    .locals 0

    invoke-virtual {p0, p1}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->setReplyNow(Ljava/lang/String;)V

    return-void
.end method

.method public onBridgeReply(Ljava/lang/String;)V
    .locals 0

    invoke-virtual {p0, p1}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->showReply(Ljava/lang/String;)V

    return-void
.end method

.method public onBridgeFailure(Ljava/lang/String;)V
    .locals 2

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "Binder failed: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p0, v0}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->showReply(Ljava/lang/String;)V

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

.class final Lcom/tuanjie/urasclient2/CentralBrainPanelController$UiUpdate;
.super Ljava/lang/Object;
.implements Ljava/lang/Runnable;
.source "CentralBrainPanelController.java"

.field private final controller:Lcom/tuanjie/urasclient2/CentralBrainPanelController;

.field private final text:Ljava/lang/String;


.method public constructor <init>(Lcom/tuanjie/urasclient2/CentralBrainPanelController;Ljava/lang/String;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController$UiUpdate;->controller:Lcom/tuanjie/urasclient2/CentralBrainPanelController;

    iput-object p2, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController$UiUpdate;->text:Ljava/lang/String;

    return-void
.end method

.method public run()V
    .locals 2

    iget-object v0, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController$UiUpdate;->controller:Lcom/tuanjie/urasclient2/CentralBrainPanelController;

    iget-object v1, p0, Lcom/tuanjie/urasclient2/CentralBrainPanelController$UiUpdate;->text:Ljava/lang/String;

    invoke-virtual {v0, v1}, Lcom/tuanjie/urasclient2/CentralBrainPanelController;->setReplyNow(Ljava/lang/String;)V

    return-void
.end method

(() => {
  const validViews = ["care", "hvac", "seat", "execution"];
  const panel = document.getElementById("cockpitPanel");
  const strip = panel.querySelector(".execution-strip");
  const stripTitle = document.getElementById("stripTitle");
  const stripDetail = document.getElementById("stripDetail");
  const stripAction = document.getElementById("stripAction");
  const temperature = document.getElementById("targetTemperature");

  const stripState = {
    care: ["当前无执行任务", "选择场景或手动调节以开始", false, "查看"],
    hvac: ["正在调整主驾温度", "目标 24.0°C · 等待回读确认", true, "详情"],
    seat: ["座椅设置已预览", "主驾加热 2 档 · 靠背 28°", false, "提交"],
    execution: ["舒适升温 · 正在确认", "1 项已完成 · 1 项执行中", true, "收起"]
  };

  function scaleStage() {
    const scale = Math.min(window.innerWidth / 1920, window.innerHeight / 1080);
    document.documentElement.style.setProperty("--stage-scale", String(scale));
  }

  function updateStrip(view) {
    const [title, detail, running, action] = stripState[view];
    stripTitle.textContent = title;
    stripDetail.textContent = detail;
    stripAction.textContent = action;
    strip.classList.toggle("running", running);
  }

  function setView(view, updateUrl = true) {
    const selected = validViews.includes(view) ? view : "care";
    document.body.dataset.view = selected;
    panel.querySelectorAll("[data-view]").forEach((surface) => {
      surface.classList.toggle("active", surface.dataset.view === selected);
    });
    panel.querySelectorAll("[data-view-target]").forEach((button) => {
      const active = button.dataset.viewTarget === selected;
      button.classList.toggle("active", active);
      button.setAttribute("aria-selected", String(active));
      button.tabIndex = active ? 0 : -1;
    });
    updateStrip(selected);
    if (updateUrl) {
      const url = new URL(window.location.href);
      url.searchParams.set("view", selected);
      history.replaceState(null, "", url);
    }
  }

  function showPanel() {
    panel.classList.remove("hidden");
    panel.setAttribute("aria-hidden", "false");
  }

  function hidePanel() {
    panel.classList.add("hidden");
    panel.setAttribute("aria-hidden", "true");
  }

  panel.querySelectorAll("[data-view-target], [data-view-link]").forEach((button) => {
    button.addEventListener("click", () => {
      const view = button.dataset.viewTarget || button.dataset.viewLink;
      setView(view);
    });
  });

  panel.querySelectorAll("[data-run-scene]").forEach((button) => {
    button.addEventListener("click", () => {
      stripState.execution = [
        button.dataset.runScene === "fatigue" ? "疲劳关怀 · 正在评估" : "舒适升温 · 正在确认",
        "策略已通过 · Effect 逐项回读",
        true,
        "收起"
      ];
      setView("execution");
    });
  });

  panel.querySelectorAll("[data-temp-step]").forEach((button) => {
    button.addEventListener("click", () => {
      const next = Math.max(16, Math.min(30, Number(temperature.textContent) + Number(button.dataset.tempStep)));
      temperature.textContent = next.toFixed(1);
      stripState.hvac = ["正在调整主驾温度", `目标 ${next.toFixed(1)}°C · 等待回读确认`, true, "详情"];
      updateStrip("hvac");
    });
  });

  panel.querySelectorAll(".segmented-control").forEach((control) => {
    control.querySelectorAll("button").forEach((button) => {
      button.addEventListener("click", () => {
        control.querySelectorAll("button").forEach((item) => item.classList.remove("active"));
        button.classList.add("active");
      });
    });
  });

  panel.querySelectorAll(".mode-control, .power-button, .toggle-row").forEach((button) => {
    button.addEventListener("click", () => {
      button.classList.toggle("active");
      button.setAttribute("aria-pressed", String(button.classList.contains("active")));
    });
  });

  panel.querySelectorAll("[data-level-group]").forEach((group) => {
    group.querySelectorAll("button").forEach((button) => {
      button.addEventListener("click", () => {
        group.querySelectorAll("button").forEach((item) => item.classList.remove("active", "warm", "cool"));
        button.classList.add("active", group.dataset.levelGroup === "heat" ? "warm" : "cool");
        if (button.textContent !== "0") {
          const oppositeName = group.dataset.levelGroup === "heat" ? "vent" : "heat";
          const opposite = panel.querySelector(`[data-level-group="${oppositeName}"]`);
          opposite.querySelectorAll("button").forEach((item, index) => {
            item.classList.toggle("active", index === 0);
            item.classList.toggle(oppositeName === "heat" ? "warm" : "cool", index === 0);
          });
        }
      });
    });
  });

  document.getElementById("closePanel").addEventListener("click", hidePanel);
  document.getElementById("panelTrigger").addEventListener("click", () => {
    panel.classList.contains("hidden") ? showPanel() : hidePanel();
  });

  document.getElementById("stage").addEventListener("click", (event) => {
    if (!panel.contains(event.target) && event.target.id !== "panelTrigger") {
      hidePanel();
    }
  });

  window.addEventListener("keydown", (event) => {
    if (event.key === "Escape") hidePanel();
    const index = Number(event.key) - 1;
    if (index >= 0 && index < validViews.length) {
      showPanel();
      setView(validViews[index]);
    }
  });

  window.addEventListener("resize", scaleStage);
  scaleStage();

  const requestedView = new URL(window.location.href).searchParams.get("view") || "care";
  setView(requestedView, false);
  if (new URL(window.location.href).searchParams.get("panel") === "hidden") hidePanel();

  Promise.all(Array.from(document.images).map((image) => image.decode().catch(() => undefined))).then(() => {
    window.__COCKPIT_HMI_READY__ = true;
    document.body.dataset.ready = "true";
  });
})();

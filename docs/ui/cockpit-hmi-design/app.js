(() => {
  const stage = document.querySelector(".design-stage");
  const panel = document.querySelector(".brain-panel");
  const launcher = document.querySelector(".panel-launcher");
  const drawer = document.querySelector(".device-drawer");
  const tabs = [...document.querySelectorAll(".flow-tab")];
  const surfaces = [...document.querySelectorAll(".surface")];
  const input = document.querySelector("#intent-input");
  const quoteTargets = [...document.querySelectorAll("[data-intent-quote]")];
  const strip = document.querySelector(".session-strip");
  const stripLabel = document.querySelector("[data-session-label]");
  const stripDetail = document.querySelector("[data-session-detail]");
  const views = ["intent", "plan", "execution", "result"];

  const stripState = {
    intent: ["等待场景输入", "一句话描述你的感受或目的", "idle"],
    plan: ["恢复精力 · 等待安全确认", "计划已生成，低风险动作可自动执行", "active"],
    execution: ["恢复精力 · 自动执行中", "3 个 Effect 已下发，正在等待座椅回读", "active"],
    result: ["恢复精力 · 已完成", "3 个 Effect 已通过回读确认", "completed"],
  };

  const drawerData = {
    climate: {
      title: "HVAC Effect 详情",
      values: [
        ["目标温度", "23.5°C", "Plan target"],
        ["设备回读", "23.5°C", "VERIFIED · SIMULATED"],
        ["模式", "AUTO / 新风", "CapabilityCatalog allowed"],
        ["风量", "2 档", "reported=desired"],
      ],
    },
    seat: {
      title: "Seat Effect 详情",
      values: [
        ["靠背目标", "42°", "approval required"],
        ["当前回读", "34°", "APPLYING · 2/3"],
        ["座椅通风", "1 档", "VERIFIED"],
        ["安全上下文", "P / 0 km/h", "context revision #184"],
      ],
    },
    media: {
      title: "Media Effect 详情",
      values: [
        ["当前内容", "Recover Focus", "built-in media catalog"],
        ["剩余时间", "14:42", "PLAYING"],
        ["执行来源", "AIOS Plan", "scene.fatigue.assist.v1"],
        ["停止能力", "可用", "governed cancel"],
      ],
    },
    overview: {
      title: "本次方案设备状态",
      values: [
        ["HVAC", "23.5°C", "READBACK VERIFIED"],
        ["Seat", "通风 1 / 42°", "READBACK VERIFIED"],
        ["Media", "Recover Focus", "PLAYING"],
        ["Navigation", "未执行", "SUGGESTION ONLY"],
      ],
    },
  };

  function scaleStage() {
    const scale = Math.min(window.innerWidth / 1920, window.innerHeight / 1080);
    stage.style.transform = `translate(-50%, -50%) scale(${scale})`;
  }

  function showView(view, updateUrl = true) {
    const normalized = views.includes(view) ? view : "intent";
    const activeIndex = views.indexOf(normalized);
    surfaces.forEach((surface) => surface.classList.toggle("active", surface.dataset.view === normalized));
    tabs.forEach((tab, index) => {
      tab.classList.toggle("active", tab.dataset.target === normalized);
      tab.classList.toggle("completed", index < activeIndex);
      tab.setAttribute("aria-current", tab.dataset.target === normalized ? "step" : "false");
    });

    const [label, detail, state] = stripState[normalized];
    stripLabel.textContent = label;
    stripDetail.textContent = detail;
    strip.classList.toggle("active-session", state === "active");
    strip.classList.toggle("completed-session", state === "completed");
    closeDrawer();

    if (updateUrl) {
      const url = new URL(window.location.href);
      url.searchParams.set("view", normalized);
      window.history.replaceState({}, "", url);
    }
  }

  function hidePanel() {
    panel.classList.add("hidden");
    launcher.classList.add("visible");
  }

  function showPanel() {
    panel.classList.remove("hidden");
    launcher.classList.remove("visible");
  }

  function openDrawer(kind) {
    const data = drawerData[kind] || drawerData.overview;
    drawer.querySelector("[data-drawer-title]").textContent = data.title;
    drawer.querySelector("[data-drawer-content]").innerHTML = data.values
      .map(([label, value, detail]) => `<div><span>${label}</span><strong>${value}</strong><small>${detail}</small></div>`)
      .join("");
    drawer.classList.add("open");
    drawer.setAttribute("aria-hidden", "false");
  }

  function closeDrawer() {
    drawer.classList.remove("open");
    drawer.setAttribute("aria-hidden", "true");
  }

  tabs.forEach((tab) => tab.addEventListener("click", () => showView(tab.dataset.target)));

  document.querySelector(".intent-composer").addEventListener("submit", (event) => {
    event.preventDefault();
    const value = input.value.trim();
    if (!value) {
      input.focus();
      return;
    }
    quoteTargets.forEach((target) => {
      target.textContent = value;
    });
    showView("plan");
  });

  document.querySelectorAll("[data-suggestion]").forEach((button) => {
    button.addEventListener("click", () => {
      input.value = button.dataset.suggestion;
      input.focus();
    });
  });

  document.querySelector("[data-action='approve-plan']").addEventListener("click", () => showView("execution"));
  document.querySelector("[data-action='complete-session']").addEventListener("click", () => showView("result"));
  document.querySelector("[data-action='open-execution']").addEventListener("click", () => showView("execution"));
  document.querySelectorAll("[data-detail]").forEach((button) => button.addEventListener("click", () => openDrawer(button.dataset.detail)));
  document.querySelector("[data-action='close-drawer']").addEventListener("click", closeDrawer);
  document.querySelector(".close-panel").addEventListener("click", hidePanel);
  document.querySelector(".outside-dismiss").addEventListener("click", hidePanel);
  launcher.addEventListener("click", showPanel);

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      if (drawer.classList.contains("open")) closeDrawer();
      else hidePanel();
      return;
    }
    const index = Number(event.key) - 1;
    if (index >= 0 && index < views.length) showView(views[index]);
  });

  window.addEventListener("resize", scaleStage);
  scaleStage();
  showView(new URLSearchParams(window.location.search).get("view") || "intent", false);

  Promise.all(
    [...document.images].map((img) => (img.complete ? Promise.resolve() : new Promise((resolve) => {
      img.addEventListener("load", resolve, { once: true });
      img.addEventListener("error", resolve, { once: true });
    }))),
  ).then(() => {
    window.__COCKPIT_HMI_READY__ = true;
  });
})();

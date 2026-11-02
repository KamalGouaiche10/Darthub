/**
 * Aggiunge ai campi password il comando per mostrarne il contenuto.
 *
 * @param {Element} root Elemento entro cui cercare i campi password.
 * 
 */
export function attivaMostraPassword(root) {
  root.querySelectorAll('input[type="password"]').forEach((input) => {
    if (input.parentElement.classList.contains("password-field")) return;

    const wrapper = document.createElement("div");
    wrapper.className = "password-field";
    input.before(wrapper);
    wrapper.append(input);

    const nomeCampo = root.querySelector(`label[for="${input.id}"]`)?.textContent.trim().toLocaleLowerCase("it") || "password";
    const toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "password-toggle";
    toggle.setAttribute("aria-controls", input.id);
    toggle.setAttribute("aria-pressed", "false");
    toggle.setAttribute("aria-label", `Mostra ${nomeCampo}`);
    toggle.title = `Mostra ${nomeCampo}`;
    toggle.textContent = "Mostra";
    toggle.addEventListener("click", () => {
      const visibile = input.type === "password";
      input.type = visibile ? "text" : "password";
      toggle.setAttribute("aria-pressed", String(visibile));
      toggle.setAttribute("aria-label", `${visibile ? "Nascondi" : "Mostra"} ${nomeCampo}`);
      toggle.title = `${visibile ? "Nascondi" : "Mostra"} ${nomeCampo}`;
      toggle.textContent = visibile ? "Nascondi" : "Mostra";
    });
    wrapper.append(toggle);
  });
}

/**
 * Ripristina la visualizzazione nascosta delle password nel contenitore.
 *
 * @param {Element} root Elemento entro cui cercare i campi password.
 * 
 */
export function nascondiPassword(root) {
  root.querySelectorAll(".password-field").forEach((wrapper) => {
    if (wrapper.querySelector("input").type === "text") {
      wrapper.querySelector(".password-toggle").click();
    }
  });
}

const urlInput = document.getElementById("url");
const shortenBtn = document.getElementById("shortenBtn");
const result = document.getElementById("result");
const shortUrl = document.getElementById("shortUrl");
const copyBtn = document.getElementById("copyBtn");
const message = document.getElementById("message");

shortenBtn.addEventListener("click", async () => {

    message.textContent = "";
    result.classList.add("hidden");

    const url = urlInput.value.trim();

    if (url === "") {
        message.textContent = "Ingrese una URL.";
        return;
    }

    try {

        const response = await fetch("/link", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                url: url
            })
        });

        if (!response.ok) {

            const error = await response.text();

            message.textContent = error;

            return;
        }

        const data = await response.json();

        shortUrl.value = data.shortUrl;

        result.classList.remove("hidden");

    } catch (error) {

        message.textContent = "No fue posible conectar con el servidor.";

    }

});

copyBtn.addEventListener("click", async () => {

    try {

        await navigator.clipboard.writeText(shortUrl.value);

        copyBtn.textContent = "¡Copiado!";

        setTimeout(() => {

            copyBtn.textContent = "Copiar";

        }, 1500);

    } catch (error) {

        message.textContent = "No se pudo copiar la URL.";

    }

});
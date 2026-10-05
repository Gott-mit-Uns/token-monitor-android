import http from "node:http";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createShowcaseResponses } from "./showcase-data.mjs";

const protocolRoot = join(dirname(fileURLToPath(import.meta.url)), "..", "app", "src", "test", "resources", "protocol");
const fixture = async (version, name) => readFile(join(protocolRoot, version, name), "utf8");
let responses = {
    "/api/health": await fixture("v0.66.0", "health.json"),
    "/api/stats": await fixture("v0.66.0", "stats.json"),
    "/api/devices": await fixture("v0.66.0", "devices.json"),
    "/api/history": await fixture("v0.66.0", "history.json"),
    "/api/subscriptions": await fixture("v0.66.0", "subscriptions.json"),
};

const showcaseMode = process.env.TOKEN_MONITOR_SHOWCASE === "1";
if (showcaseMode) {
    const showcase = createShowcaseResponses(process.env.TOKEN_MONITOR_SHOWCASE_DATE);
    responses = Object.fromEntries(Object.entries(showcase).map(([name, value]) => [
        `/api/${name}`,
        JSON.stringify(value),
    ]));
}

const port = Number.parseInt(process.env.TOKEN_MONITOR_FIXTURE_PORT ?? "17321", 10);
const secret = showcaseMode ? "showcase" : "fixture-secret";
if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("TOKEN_MONITOR_FIXTURE_PORT must be a valid TCP port.");
}

const server = http.createServer((request, response) => {
    if (request.url === "/api/health") {
        response.writeHead(200, { "Content-Type": "application/json" });
        response.end(responses[request.url]);
        return;
    }
    if (request.headers.authorization !== `Bearer ${secret}`) {
        response.writeHead(401, { "Content-Type": "application/json" });
        response.end('{"error":"unauthorized"}');
        return;
    }
    if (request.url === "/api/stats/stream") {
        response.writeHead(200, {
            "Cache-Control": "no-cache",
            Connection: "keep-alive",
            "Content-Type": "text/event-stream",
        });
        const streamEvent = JSON.stringify({
            at: JSON.parse(responses["/api/stats"]).updatedAt,
            stats: JSON.parse(responses["/api/stats"]),
        });
        response.write(`event: snapshot\ndata: ${streamEvent}\n\n`);
        const heartbeat = setInterval(() => response.write(": hb\n\n"), 30_000);
        request.on("close", () => clearInterval(heartbeat));
        return;
    }
    if (responses[request.url]) {
        response.writeHead(200, { "Content-Type": "application/json" });
        response.end(responses[request.url]);
        return;
    }
    response.writeHead(404, { "Content-Type": "application/json" });
    response.end('{"error":"not_found"}');
});

server.listen(port, "127.0.0.1", () => {
    const mode = showcaseMode ? "showcase" : "test fixture";
    console.log(`Sanitized Token Monitor ${mode} Hub listening on 127.0.0.1:${port}`);
});

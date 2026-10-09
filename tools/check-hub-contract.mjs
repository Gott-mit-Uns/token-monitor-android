import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import http from "node:http";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const script = fileURLToPath(import.meta.url);
const root = resolve(dirname(script), "..");
const args = process.argv.slice(2);
const option = name => { const i = args.indexOf(name); return i < 0 ? undefined : args[i + 1]; };

async function isolatedContract(source, output, upstream) {
    const require = createRequire(join(source, "package.json"));
    assert.equal(require("./package.json").version, upstream.version, "Pinned desktop source has another version");
    const { createHub } = require("./src/hub/server.js");
    const now = new Date().toISOString();
    const day = now.slice(0, 10);
    const period = { totalTokens: 1234, costUsd: 0.42, unpricedTokens: 321, clients: { mcode: 1234 },
        clientUnpricedTokens: { mcode: 321 }, modelUnpricedTokens: { "minimax-m2.5": 321 },
        clientModelUnpricedTokens: { mcode: { "minimax-m2.5": 321 } },
        projects: { synthetic: { projectId: "synthetic", label: "Synthetic project", totalTokens: 1234, costUsd: 0.42, unpricedTokens: 321 } },
        sessions: { "mcode:contract-session": { sessionId: "contract-session", client: "mcode",
            title: "Synthetic shared title", totalTokens: 1234, unpricedTokens: 321, costUsd: 0.42, prompt: "NEVER_EXPORT_PROMPT", messages: ["NEVER_EXPORT_MESSAGES"] } },
        clientCosts: { mcode: 0.42 }, models: { "minimax-m2.5": 1234 },
        modelCosts: { "minimax-m2.5": 0.42 }, clientModels: { mcode: { "minimax-m2.5": 1234 } } };
    const device = { deviceId: "contract-desktop", hostname: "Synthetic desktop", platform: "linux",
        agentVersion: upstream.version, updatedAt: now, trackedClients: ["mcode"],
        periods: { today: period, month: period, allTime: period }, historyAvailable: true,
        history: { daily: [{ date: day, tokens: 1234, cost: 0.42, unpricedTokens: 321,
            perClient: { mcode: { tokens: 1234, cost: 0.42, unpricedTokens: 321 } },
            perModel: { "minimax-m2.5": { tokens: 1234, cost: 0.42, unpricedTokens: 321 } } }], monthly: [] } };
    const secret = "isolated-contract-secret";
    const hub = createHub({ port: 0, host: "127.0.0.1", secret, dataFile: join(source, "synthetic-data.json"), broadcastDelayMs: 5,
        logger: { error: error => { throw error; } } });
    // This setup populates only the temporary test Hub. Every HTTP request below is a GET.
    hub.ingest(device);
    let stream;
    const frames = [], pending = [];
    const nextFrame = () => frames.length ? Promise.resolve(frames.shift()) : new Promise((resolveFrame, reject) => {
        const entry = { resolve: frame => { clearTimeout(timer); resolveFrame(frame); } };
        const timer = setTimeout(() => { pending.splice(pending.indexOf(entry), 1); reject(new Error("Timed out waiting for Hub stream event")); }, 5000);
        pending.push(entry);
    });
    await hub.start();
    try {
        assert.equal(hub.bindHost, "127.0.0.1");
        const base = `http://127.0.0.1:${hub.server.address().port}`;
        const get = async (path, authenticated = true) => fetch(`${base}${path}`, {
            headers: authenticated ? { Authorization: `Bearer ${secret}` } : {},
            redirect: "error", signal: AbortSignal.timeout(5000),
        });
        assert.equal((await get("/api/stats", false)).status, 401, "Hub unexpectedly serves statistics without authentication");
        const responses = {};
        for (const name of ["health", "stats", "devices", "history", "subscriptions"]) {
            const response = await get(`/api/${name}`, name !== "health");
            assert.equal(response.status, 200, `${name} GET failed`);
            responses[`${name}.json`] = await response.json();
        }
        assert.equal(responses["health.json"].role, "hub");
        assert.equal(responses["health.json"].secretRequired, true);
        assert.equal(responses["stats.json"].periods.today.totalTokens, 1234);
        assert.equal(responses["stats.json"].periods.today.clients.mcode, 1234);
        assert.equal(responses["stats.json"].periods.today.unpricedTokens, 321);
        assert.equal(responses["stats.json"].periods.today.clientModelUnpricedTokens.mcode["minimax-m2.5"], 321);
        assert.ok(!JSON.stringify(responses["stats.json"]).includes("Synthetic shared title"), "Titles must be absent by default");
        assert.ok(!JSON.stringify(responses["stats.json"]).includes("NEVER_EXPORT"), "Conversation content must never cross the Hub");
        assert.ok(responses["stats.json"].syncSettingsRevisions, "Shared settings revisions must remain additive");
        assert.equal(responses["devices.json"].devices[0].deviceId, device.deviceId);
        assert.ok(responses["history.json"].daily.some(point => point.tokens === 1234));
        assert.ok(Array.isArray(responses["subscriptions.json"].subscriptions));
        stream = http.get(`${base}/api/stats/stream`, { headers: { Authorization: `Bearer ${secret}`, "x-token-monitor-stream": "2" } }, response => {
            assert.equal(response.statusCode, 200);
            let buffered = "";
            response.setEncoding("utf8");
            response.on("data", chunk => {
                buffered += chunk;
                while (buffered.includes("\n\n")) {
                    const boundary = buffered.indexOf("\n\n");
                    const block = buffered.slice(0, boundary);
                    buffered = buffered.slice(boundary + 2);
                    const event = /^event: (.+)$/m.exec(block)?.[1];
                    const data = /^data: (.+)$/m.exec(block)?.[1];
                    if (!event || !data) continue;
                    const frame = { event, data: JSON.parse(data) };
                    if (pending.length) pending.shift().resolve(frame); else frames.push(frame);
                }
            });
        });
        stream.on("error", () => {}); // The bounded frame wait supplies the failure message.
        const snapshot = await nextFrame();
        assert.equal(snapshot.event, "snapshot");
        assert.equal(snapshot.data.stats.periods.today.totalTokens, 1234);
        responses["stream-snapshot.json"] = snapshot.data;
        // A metadata-only ingest must produce v2 freshness, retaining the existing counters.
        hub.ingest({ ...device, updatedAt: new Date().toISOString() });
        const freshness = await nextFrame();
        assert.equal(freshness.event, "freshness");
        assert.equal(freshness.data.stats.periods, undefined);
        responses["freshness.json"] = freshness.data;
        hub.ingest({ ...device, periods: { ...device.periods, today: { ...period, totalTokens: 1235,
            clients: { mcode: 1235 }, models: { "minimax-m2.5": 1235 }, clientModels: { mcode: { "minimax-m2.5": 1235 } } } } });
        const changed = await nextFrame();
        assert.equal(changed.event, "stats");
        assert.equal(changed.data.stats.periods.today.totalTokens, 1235);
        responses["stream-stats.json"] = changed.data;
        // Consent setup is confined to another disposable Hub; Android still makes no writes.
        const titleHub = createHub({ port: 0, host: "127.0.0.1", secret, syncSessionTitles: true,
            dataFile: join(source, "synthetic-titles.json"), broadcastDelayMs: 5 });
        const policy = titleHub.setSyncTitlePolicy(device.deviceId, true);
        titleHub.ingest({ ...device, sessionTitleSyncGeneration: policy.generation });
        responses["titles-enabled.json"] = titleHub.getStats();
        assert.ok(JSON.stringify(responses["titles-enabled.json"]).includes("Synthetic shared title"));
        assert.ok(!JSON.stringify(responses["titles-enabled.json"]).includes("NEVER_EXPORT"));
        titleHub.setSyncTitlePolicy(device.deviceId, false);
        responses["titles-revoked.json"] = titleHub.getStats();
        assert.ok(!JSON.stringify(responses["titles-revoked.json"]).includes("Synthetic shared title"), "Revocation must clear stored titles");
        const dotsHub = createHub({ port: 0, host: "127.0.0.1", secret, dataFile: join(source, "synthetic-dots.json") });
        const dotsPeriod = { totalTokens: 400, costUsd: 0, unpricedTokens: 400, clients: { codex: 400 },
            clientUnpricedTokens: { codex: 400 }, models: { "unpriced-model": 400 },
            modelUnpricedTokens: { "unpriced-model": 400 },
            sessions: { dots: { sessionId: "dots", client: "codex", totalTokens: 400, unpricedTokens: 400,
                usageSource: "codex-dots-local", usageCoverage: "observed-only", codexLocalSessionKeys: ["NEVER_EXPORT_LOCAL_KEY"] } } };
        dotsHub.ingest({ ...device, periods: { today: dotsPeriod } });
        responses["dots.json"] = dotsHub.getStats();
        assert.equal(Object.values(responses["dots.json"].periods.today.sessions)[0].usageCoverage, "observed-only");
        assert.ok(!JSON.stringify(responses["dots.json"]).includes("NEVER_EXPORT_LOCAL_KEY"));
        responses["contract.json"] = { upstream, generatedAt: new Date().toISOString(), synthetic: true,
            expectedTodayTokens: 1234, expectedUpdatedTokens: 1235 };
        await mkdir(output, { recursive: true });
        for (const [name, value] of Object.entries(responses)) await writeFile(join(output, name), `${JSON.stringify(value, null, 2)}\n`);
        console.log(`Actual ${upstream.tag} Hub passed authenticated reads, snapshot, freshness and changed-stat checks. Synthetic responses: ${output}`);
    } finally {
        stream?.destroy();
        hub.server.closeAllConnections();
        await hub.stop();
    }
}

const workerSource = option("--isolated-source");
if (workerSource) {
    const upstream = JSON.parse(await readFile(join(workerSource, "contract-upstream.json"), "utf8"));
    await isolatedContract(workerSource, resolve(option("--output")), upstream);
} else {
    const upstream = JSON.parse(await readFile(join(root, "upstream.json"), "utf8"));
    const approved = option("--approved-commit");
    assert.equal(approved, upstream.commit, "Explicit --approved-commit must match the reviewed released upstream commit");
    assert.match(approved, /^[a-f0-9]{40}$/);
    const repository = resolve(option("--upstream-repo") || "");
    assert.ok(option("--upstream-repo"), "Provide the read-only desktop source repository");
    assert.equal(execFileSync("git", ["-C", repository, "rev-parse", `${approved}^{commit}`], { encoding: "utf8" }).trim(), approved);
    const directory = await mkdtemp(join(tmpdir(), "token-monitor-hub-contract-"));
    try {
        // Export committed Hub/shared files only; never use the working tree, collectors, data, or .env.
        const archive = execFileSync("git", ["-C", repository, "archive", "--format=tar", approved, "package.json", "src/hub", "src/shared"], { maxBuffer: 32 * 1024 * 1024 });
        execFileSync("tar", ["-xf", "-", "-C", directory], { input: archive });
        await writeFile(join(directory, "contract-upstream.json"), JSON.stringify(upstream));
        const env = Object.fromEntries(["PATH", "Path", "SystemRoot", "SYSTEMROOT", "WINDIR", "TEMP", "TMP"]
            .filter(key => process.env[key]).map(key => [key, process.env[key]]));
        Object.assign(env, { HOME: directory, USERPROFILE: directory, APPDATA: directory, LOCALAPPDATA: directory,
            XDG_CONFIG_HOME: directory, TOKEN_MONITOR_SHARED_DIR: directory, TZ: "UTC" });
        execFileSync(process.execPath, [script, "--isolated-source", directory, "--output", resolve(option("--output") || join(root, "app/build/hub-contract"))],
            { env, cwd: directory, stdio: "inherit", timeout: 30_000 });
    } finally {
        // mkdtemp created this exact directory under the platform temporary directory.
        assert.equal(dirname(directory), resolve(tmpdir()));
        assert.ok(directory.startsWith(join(resolve(tmpdir()), "token-monitor-hub-contract-")));
        await rm(directory, { recursive: true, force: true });
    }
}

// Servidor de avisos do AppLourdinha (Cloudflare Worker)
// Bindings: KV = namespace "lourdinha"; secret FCM_SA = JSON da conta de servico do Firebase.
const J = (o, s = 200) => new Response(JSON.stringify(o), { status: s, headers: { "content-type": "application/json" } });
const okCode = c => typeof c === "string" && /^[A-Za-z0-9_-]{20,64}$/.test(c);
const b64u = b => btoa(String.fromCharCode(...new Uint8Array(b))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const enc = s => b64u(new TextEncoder().encode(s));

async function accessToken(env) {
  const cached = await env.KV.get("oauth", "json");
  if (cached && cached.exp > Date.now() + 60000) return cached.t;
  const sa = JSON.parse(env.FCM_SA), now = Math.floor(Date.now() / 1000);
  const head = enc(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = enc(JSON.stringify({ iss: sa.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging", aud: sa.token_uri, iat: now, exp: now + 3600 }));
  const pem = sa.private_key.replace(/-----[A-Z ]+-----/g, "").replace(/\s+/g, "");
  const key = await crypto.subtle.importKey("pkcs8", Uint8Array.from(atob(pem), c => c.charCodeAt(0)), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = b64u(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(head + "." + claim)));
  const r = await fetch(sa.token_uri, { method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" }, body: "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + head + "." + claim + "." + sig });
  const j = await r.json();
  await env.KV.put("oauth", JSON.stringify({ t: j.access_token, exp: Date.now() + 3300000 }), { expirationTtl: 3500 });
  return j.access_token;
}

async function fanout(env, k, list, except, data, only) {
  const dest = only || list.filter(t => t !== except);
  if (!dest.length) return 0;
  const tok = await accessToken(env), pid = JSON.parse(env.FCM_SA).project_id;
  let sent = 0; const keep = except && list.includes(except) ? [except] : [];
  for (const t of dest) {
    const r = await fetch("https://fcm.googleapis.com/v1/projects/" + pid + "/messages:send", {
      method: "POST", headers: { authorization: "Bearer " + tok, "content-type": "application/json" },
      body: JSON.stringify({ message: { token: t, data: { title: data.title, body: data.body, kind: data.kind, ts: String(Date.now()) }, android: { priority: "HIGH" } } })
    });
    if (r.ok) { sent++; keep.push(t); } else if (r.status !== 404 && r.status !== 400) keep.push(t);
  }
  if (!only && keep.length !== list.length) await env.KV.put(k, JSON.stringify(keep));
  return sent;
}

export default {
  async fetch(req, env) {
    if (req.method !== "POST") return J({ ok: true });
    const url = new URL(req.url);
    let b; try { b = await req.json(); } catch (e) { return J({ erro: "json" }, 400); }
    if (!okCode(b.c)) return J({ erro: "codigo" }, 400);
    const k = "c:" + b.c;
    let list = (await env.KV.get(k, "json")) || [];
    if (url.pathname === "/reg") {              // cuidador registra o aparelho
      if (typeof b.t !== "string" || b.t.length < 20 || b.t.length > 400) return J({ erro: "token" }, 400);
      if (!list.includes(b.t)) list.push(b.t);
      list = list.slice(-10);
      await env.KV.put(k, JSON.stringify(list));
      return J({ ok: true, n: list.length });
    }
    if (url.pathname === "/unreg") {            // cuidador sai
      list = list.filter(x => x !== b.t); await env.KV.put(k, JSON.stringify(list)); return J({ ok: true });
    }
    if (url.pathname === "/state") {            // celular da Lourdinha publica o resumo dos dados
      if (typeof b.d !== "string" || b.d.length > 150000) return J({ erro: "tamanho" }, 400);
      await env.KV.put("s:" + b.c, b.d, { expirationTtl: 60 * 86400 });
      return J({ ok: true });
    }
    if (url.pathname === "/cfgset") {           // cuidador publica definicoes secretas
      if (typeof b.d !== "string" || b.d.length > 2000) return J({ erro: "tamanho" }, 400);
      await env.KV.put("g:" + b.c, b.d); return J({ ok: true });
    }
    if (url.pathname === "/cfgget") { return J({ d: (await env.KV.get("g:" + b.c)) || "" }); }
    if (url.pathname === "/get") {              // cuidador le o resumo
      return J({ d: (await env.KV.get("s:" + b.c)) || "" });
    }
    if (url.pathname === "/prof") {             // cuidador publica seus dados (nome, aparelho, bateria, local)
      if (typeof b.t !== "string" || b.t.length < 20) return J({ erro: "token" }, 400);
      const id = b.t.slice(-12), pk = "p:" + b.c, all = (await env.KV.get(pk, "json")) || {};
      const old = all[id] || {};
      all[id] = { t: b.t, name: String(b.name || old.name || "").slice(0, 40), dev: String(b.dev || old.dev || "").slice(0, 60), bat: Number.isFinite(b.bat) ? b.bat : (old.bat ?? null), ts: Date.now(),
        lat: Number.isFinite(b.lat) ? b.lat : old.lat, lon: Number.isFinite(b.lon) ? b.lon : old.lon, place: b.place !== undefined ? String(b.place).slice(0, 120) : old.place, lts: Number.isFinite(b.lat) ? Date.now() : old.lts };
      const ids = Object.keys(all); if (ids.length > 10) delete all[ids.sort((x, y) => all[x].ts - all[y].ts)[0]];
      await env.KV.put(pk, JSON.stringify(all)); return J({ ok: true, id });
    }
    if (url.pathname === "/profs") {            // lista os cuidadores (sem expor o token)
      const all = (await env.KV.get("p:" + b.c, "json")) || {};
      return J({ d: Object.keys(all).map(id => { const x = all[id]; return { id, name: x.name, dev: x.dev, bat: x.bat, ts: x.ts, lat: x.lat, lon: x.lon, place: x.place, lts: x.lts }; }) });
    }
    if (url.pathname === "/locreq") {           // pede ao aparelho de um cuidador que atualize seus dados e a localizacao
      const all = (await env.KV.get("p:" + b.c, "json")) || {}, x = all[String(b.id || "")];
      if (!x) return J({ erro: "id" }, 404);
      return J({ ok: true, n: await fanout(env, k, list, null, { title: "", body: "", kind: "locreq" }, [x.t]) });
    }
    if (url.pathname === "/send") {             // celular da Lourdinha envia um aviso
      const title = String(b.title || "AppLourdinha").slice(0, 80), body = String(b.body || "").slice(0, 400), kind = String(b.kind || "info").slice(0, 20);
      return J({ ok: true, n: await fanout(env, k, list, null, { title, body, kind }) });
    }
    if (url.pathname === "/care") {             // cuidador ligou: o primeiro avisa os demais
      const n = String(b.n || "").trim().slice(0, 40), r = String(b.r || "").replace(/[^A-Za-z0-9_-]/g, "").slice(0, 40), x = String(b.x || "").slice(0, 80);
      if (!n || !r) return J({ erro: "dados" }, 400);
      const ck = "k:" + b.c + ":" + r, prev = await env.KV.get(ck);
      if (prev) return J({ ok: true, first: false, by: prev });
      await env.KV.put(ck, n, { expirationTtl: 3600 });
      const sent = await fanout(env, k, list, typeof b.t === "string" ? b.t : null, { title: "AppLourdinha", body: "o cuidador " + n + " esta ligando para a Lourdinha" + (x ? " (resposta a " + x + ")" : "") + ".", kind: "contato" });
      return J({ ok: true, first: true, n: sent });
    }
    return J({ erro: "rota" }, 404);
  }
};

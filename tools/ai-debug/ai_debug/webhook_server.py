"""Flask webhook receiver for the ai-debug worker.

Routes:
  GET  /healthz                    -> {"ok": true}
  POST /webhook/firebase-result    -> enqueue a debugging job (HMAC verified)
  GET  /jobs/<job_id>              -> job status
  GET  /jobs                       -> list jobs
"""

from __future__ import annotations

import time

from flask import Flask, jsonify, request

from .worker import Worker, default_worker_paths

app = Flask(__name__)
_cfg, _store = default_worker_paths()
_worker = Worker(_cfg, _store, max_workers=2)


@app.route("/healthz")
def healthz():
    return jsonify({"ok": True})


@app.route("/webhook/firebase-result", methods=["POST"])
def firebase_result():
    raw = request.get_data()
    sig = request.headers.get("X-Signature-256", "")
    if not _worker.verify_signature(raw, sig):
        return jsonify({"error": "invalid signature"}), 401

    payload = request.get_json(force=True, silent=True)
    if not payload:
        return jsonify({"error": "invalid json"}), 400

    repo = payload.get("repo") or _cfg and None
    if not repo:
        return jsonify({"error": "no repo in payload"}), 400

    summary = payload.get("summary", {})
    if summary.get("status") not in ("failed", "failed_local_build"):
        return jsonify({"ok": True, "skipped": "status was not 'failed'"})

    run_id = str(payload.get("run_id", int(time.time())))
    job_id = _worker.enqueue_from_webhook(payload, run_id)
    return jsonify({"ok": True, "job_id": job_id}), 202


@app.route("/jobs/<job_id>")
def job_status(job_id):
    job = _store.load(job_id)
    if not job:
        return jsonify({"error": "not found"}), 404
    return jsonify(job.to_dict())


@app.route("/jobs")
def list_jobs():
    return jsonify([j.to_dict() for j in _store.list_jobs()])


def run_server(host: str = "0.0.0.0", port: int = 8080) -> None:
    app.run(host=host, port=port)

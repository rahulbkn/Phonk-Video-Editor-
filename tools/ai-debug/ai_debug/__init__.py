"""Autonomous AI Android Debugging System (ai-debug).

Orchestrator + free-model router + worker that receives Firebase Test Lab
failures, drives OpenCode with a rotating pool of FREE models, verifies fixes
with a local Gradle build, pushes to a feature/ai-fix-* branch, and opens a
GitHub PR once CI passes.
"""

__version__ = "1.0.0"

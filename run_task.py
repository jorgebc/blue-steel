#!/usr/bin/env python3
"""Entry point for the Blue Steel multi-agent AI development pipeline."""

import sys

import click


@click.command()
@click.option("--task", required=True, help="Task ID to run (e.g. F2.1)")
@click.option(
    "--mode",
    default="cloud",
    show_default=True,
    type=click.Choice(["cloud", "local"]),
    help="LLM mode: cloud (Claude) or local (Ollama)",
)
@click.option(
    "--phase",
    default="all",
    show_default=True,
    help="Pipeline phase to run (1-6 or 'all')",
)
@click.option(
    "--resume",
    is_flag=True,
    default=False,
    help="Resume an interrupted pipeline run",
)
def main(task: str, mode: str, phase: str, resume: bool) -> None:
    """Run a Blue Steel pipeline task."""
    click.echo(f"Task:   {task}")
    click.echo(f"Mode:   {mode}")
    click.echo(f"Phase:  {phase}")
    if resume:
        click.echo("Resume: enabled")

    click.echo("\nPipeline not implemented yet")
    sys.exit(0)


if __name__ == "__main__":
    main()

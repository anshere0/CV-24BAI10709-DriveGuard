import argparse
import sys
import uvicorn
from pathlib import Path

from backend.app.core.config import load_app_config
from backend.app.database.connection import init_database
from cv_engine.pipeline import PipelineConfig
from cv_engine.runner import run_headless

def init_db(config_path: str):
    print(f"Initializing database using config: {config_path}")
    init_database(config_path)
    print("Database initialized successfully.")

def run_api(args):
    config = load_app_config(args.config)
    init_db(args.config)
    
    port = config.backend.port if hasattr(config.backend, 'port') else 8000
    host = config.backend.host if hasattr(config.backend, 'host') else "0.0.0.0"
    
    print(f"Starting API on {host}:{port}")
    uvicorn.run("backend.app.main:app", host=host, port=port, reload=False)

def run_headless_analysis(args):
    config = load_app_config(args.config)
    
    if args.source.isdigit():
        source = int(args.source)
        mode = "webcam"
    elif Path(args.source).is_dir():
        source = str(args.source)
        mode = "batch"
    else:
        source = str(args.source)
        mode = "video"
        
    pipeline_config = PipelineConfig.from_app_config(
        app_config=config,
        source_mode=mode,
        source=source
    )
    pipeline_config.config_path = args.config
    if args.output:
        pipeline_config.output_directory = args.output
        
    print(f"Running headless analysis on {source}")
    result = run_headless(pipeline_config)
    print(f"Analysis complete. Score: {result.batch_report.average_score}")

def main():
    parser = argparse.ArgumentParser(description="DriveGuard - Student Edition")
    parser.add_argument("--config", type=str, default="config.yaml", help="Path to config.yaml")
    
    subparsers = parser.add_subparsers(dest="command", required=True)
    
    # API command
    api_parser = subparsers.add_parser("api", help="Start the FastAPI backend server")
    api_parser.set_defaults(func=run_api)
    
    # Run command
    run_parser = subparsers.add_parser("run", help="Run headless video analysis")
    run_parser.add_argument("source", type=str, help="Video file, directory, or camera index (e.g. 0)")
    run_parser.add_argument("--output", type=str, help="Output directory for reports", default=None)
    run_parser.set_defaults(func=run_headless_analysis)
    
    args = parser.parse_args()
    args.func(args)

if __name__ == "__main__":
    sys.exit(main())

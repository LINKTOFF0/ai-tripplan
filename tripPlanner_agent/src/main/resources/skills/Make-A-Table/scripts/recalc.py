#!/usr/bin/env python3
"""表格分析与重新计算工具，支持 .xlsx / .csv 格式。"""

import json
import sys
from pathlib import Path

try:
    import pandas as pd
except ImportError:
    print("缺少 pandas 依赖，请执行: pip install pandas openpyxl", file=sys.stderr)
    sys.exit(1)


def analyze(df: pd.DataFrame) -> dict:
    """分析 DataFrame 并返回摘要信息。"""
    numeric_cols = df.select_dtypes(include="number").columns.tolist()
    return {
        "shape": list(df.shape),
        "columns": df.columns.tolist(),
        "dtypes": {col: str(dtype) for col, dtype in df.dtypes.items()},
        "numeric_columns": numeric_cols,
        "missing": df.isnull().sum().to_dict(),
        "describe": (
            df[numeric_cols].describe().to_dict() if numeric_cols else {}
        ),
    }


def main():
    if len(sys.argv) < 2:
        print(
            "用法: python recalc.py <输入文件> [输出文件]",
            file=sys.stderr,
        )
        sys.exit(1)

    input_path = Path(sys.argv[1])
    output_path = Path(sys.argv[2]) if len(sys.argv) > 2 else None

    if not input_path.exists():
        print(f"文件不存在: {input_path}", file=sys.stderr)
        sys.exit(1)

    # 读取
    suffix = input_path.suffix.lower()
    if suffix == ".csv":
        df = pd.read_csv(input_path)
    elif suffix in (".xlsx", ".xls"):
        df = pd.read_excel(input_path)
    else:
        print(f"不支持的文件格式: {suffix}", file=sys.stderr)
        sys.exit(1)

    # 分析
    summary = analyze(df)
    print("=== 表格分析结果 ===")
    print(json.dumps(summary, ensure_ascii=False, indent=2))

    # 输出
    if output_path:
        out_suffix = output_path.suffix.lower()
        if out_suffix == ".csv":
            df.to_csv(output_path, index=False)
        elif out_suffix in (".xlsx", ".xls"):
            df.to_excel(output_path, index=False)
        else:
            print(f"不支持的输出格式: {out_suffix}", file=stderr)
            sys.exit(1)
        print(f"\n已写入: {output_path}")


if __name__ == "__main__":
    main()
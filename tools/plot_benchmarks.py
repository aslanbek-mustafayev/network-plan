#!/usr/bin/env python3
"""
Draws charts from the JMH benchmark results.

Reads app/build/reports/jmh/results.json, written by ./gradlew :app:benchmark,
and writes PNG figures plus a table of the raw numbers into results/.

Two kinds of chart, on purpose:

  * Growth charts use lines on logarithmic axes. There a diagonal line means
    the cost grows in step with the plan, and a flat line means the cost does
    not depend on the plan size at all.
  * The comparison of the steps uses bars on an ordinary axis, one small panel
    per plan size. Bars are read by their length, which stops being honest on a
    logarithmic axis, so they are never combined with one.
"""

import json
import math
from pathlib import Path

import matplotlib
matplotlib.use("Agg")  # write files, never try to open a window
import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter, NullFormatter, NullLocator

# A full run writes results.json, a quick one results-quick.json. Prefer the
# full run when both are present.
CANDIDATES = [
    Path("app/build/reports/jmh/results.json"),
    Path("app/build/reports/jmh/results-quick.json"),
]
OUTPUT = Path("results")

# The benchmark methods are named after the code they call, which tells a
# reader nothing unless they have seen the source.
STEPS = [
    ("parseOnly", "1. Read the plan text", "read"),
    ("semanticValidation", "2. Check for mistakes *", "check *"),
    ("firstCpmEvaluation", "3. Work out the schedule *", "schedule *"),
    ("coldEndToEnd", "All three steps together", "all three"),
    ("cachedProjectDuration", "Ask again for a saved schedule", None),
]

SHAPES = {
    "chain": "every activity waits for the one before it",
    "fanOutMerge": "many activities run side by side",
}

NOTE = (
    "* Steps 2 and 3 rebuild the plan from text before every measurement, so "
    "they sit higher than the step alone would."
)

REUSE_NOTE = (
    "The schedule was already worked out once, so asking again only looks the "
    "answer up. That is why the plan size\nmakes no difference here, and why "
    "the line is flat rather than climbing. The whiskers show how much the "
    "measurements\nvaried between runs, which at these tiny values is most of "
    "what is left to see."
)

GROWTH_NOTE = (
    "Both axes are logarithmic. A diagonal line means the cost grows in step "
    "with the plan size.\nA flat line means the cost does not depend on the "
    "plan size at all, which is the ideal case.\n"
    "The value in brackets is the measured growth: n^1.00 means doubling the "
    "plan doubles the cost, and flat means the size makes no difference."
)


def results_file():
    for candidate in CANDIDATES:
        if candidate.exists():
            return candidate
    raise SystemExit(
        "No benchmark results found. Run ./gradlew :app:benchmark first, "
        "or ./gradlew :app:benchmark -Pquick for a rough but much faster run."
    )


def plain_number(value, _position=None):
    """
    Tick labels people can read.

    Matplotlib writes 10^3 on logarithmic axes by default, which is precise but
    hard to read at a glance, so write 1k instead.
    """
    if value >= 1_000_000:
        return f"{value / 1_000_000:g}M"
    if value >= 1_000:
        return f"{value / 1_000:g}k"
    if value >= 1:
        return f"{value:g}"
    return f"{value:.6f}".rstrip("0").rstrip(".") or "0"


def byte_size(value, _position=None):
    """
    Tick labels people can read for memory.

    A raw byte count like 23,068,672 is hard to read, but 22 MB
    is immediately comparable to the next number.
    """
    # Steps of 1000 rather than 1024, because a logarithmic axis puts its
    # labels on powers of ten: 1000000 bytes then reads as a round 1 MB
    # instead of 977 KB.
    for limit, suffix in ((1000 ** 3, "GB"), (1000 ** 2, "MB"), (1000, "KB")):
        if value >= limit:
            return f"{value / limit:,.3g} {suffix}"
    return f"{value:,.0f} B"


def tidy_axes(axes, sizes):
    """
    Strips the chart back to what is actually being read.

    A logarithmic axis draws nine minor gridlines inside every decade, which
    hides the data under a grid, so we keep only labeled lines.
    """
    axes.set_xticks(sizes)
    axes.xaxis.set_major_formatter(FuncFormatter(plain_number))
    axes.xaxis.set_minor_locator(NullLocator())
    axes.yaxis.set_major_formatter(FuncFormatter(plain_number))
    axes.yaxis.set_minor_formatter(NullFormatter())

    axes.grid(True, which="major", alpha=0.25, linewidth=0.6)
    axes.set_axisbelow(True)
    for side in ("top", "right"):
        axes.spines[side].set_visible(False)


def read_rows(path):
    """Flattens the JMH file into one plain dict per measured point."""
    rows = []
    for entry in json.loads(path.read_text()):
        primary = entry["primaryMetric"]
        memory = entry.get("secondaryMetrics", {}).get("gc.alloc.rate.norm")
        rows.append({
            "benchmark": entry["benchmark"].rsplit(".", 1)[-1],
            "activities": int(entry["params"]["activities"]),
            "topology": entry["params"]["topology"],
            "time": primary["score"],
            "time_error": usable(primary.get("scoreError")),
            "time_unit": primary["scoreUnit"],
            "bytes": memory["score"] if memory else None,
        })
    return rows


def usable(error):
    """
    Turns JMH's error bar into a number we can plot.

    A run with too few iterations has no confidence interval, and JMH writes
    the text "NaN" rather than a number there, so draw no bar in that case.
    """
    try:
        value = float(error)
    except (TypeError, ValueError):
        return 0.0
    return 0.0 if math.isnan(value) else value


def series(rows, benchmark, topology):
    picked = [
        row for row in rows
        if row["benchmark"] == benchmark and row["topology"] == topology
    ]
    return sorted(picked, key=lambda row: row["activities"])


def draw_panel(axes, rows, topology, benchmarks, value_key, error_key,
               axis_label, sizes):
    """One panel: how the chosen measure grows with the size of the plan."""
    measured = [
        point[value_key]
        for name, _, _ in benchmarks
        for point in series(rows, name, topology)
        if point[value_key] is not None and point[value_key] > 0
    ]

    # Reusing a saved answer allocates nothing worth naming. Drawing a line
    # through figures that small would only plot measurement noise.
    if value_key == "bytes" and measured and max(measured) < 1:
        axes.text(0.5, 0.5, "No measurable memory:\nunder one byte per run",
                  ha="center", va="center", transform=axes.transAxes,
                  fontsize=12, color="#333333")
        axes.set_xticks([])
        axes.set_yticks([])
        for side in axes.spines.values():
            side.set_visible(False)
        axes.set_ylabel(axis_label)
        return

    for name, label, _ in benchmarks:
        points = [
            point for point in series(rows, name, topology)
            if point[value_key] is not None and point[value_key] > 0
        ]
        if not points:
            continue
        axes.errorbar(
            [point["activities"] for point in points],
            [point[value_key] for point in points],
            yerr=[point[error_key] for point in points] if error_key else None,
            marker="o",
            markersize=5,
            linewidth=1.6,
            capsize=3,
            label=label + exponent_note(points, value_key),
        )

    axes.set_xscale("log")
    axes.set_xlabel("Number of activities in the plan")
    axes.set_ylabel(axis_label)

    # A logarithmic axis only labels whole powers of ten, so a measure that
    # barely moves gets no labels at all, and the little noise it does have is
    # stretched into a dramatic looking zigzag. Plain scale for those.
    spread = (max(measured) / min(measured)
              if measured and min(measured) > 0 else 0)

    if spread >= 10:
        axes.set_yscale("log")
    else:
        axes.set_yscale("linear")
        axes.set_ylim(bottom=0)

    tidy_axes(axes, sizes)
    if value_key == "bytes":
        axes.yaxis.set_major_formatter(FuncFormatter(byte_size))


def growth_figure(rows, topology, benchmarks, unit, title, footnote, path):
    """
    Time on the left, memory on the right, for one group of benchmarks.

    Doing the work and reusing a saved answer get separate figures, because a
    saved answer costs so little that drawing both together leaves the middle
    of the chart empty and squashes everything else into two thin bands.
    """
    sizes = sorted({row["activities"] for row in rows})
    figure, (time_panel, memory_panel) = plt.subplots(1, 2, figsize=(13, 5.5))

    draw_panel(time_panel, rows, topology, benchmarks, "time", "time_error",
               f"Time for one run ({unit})", sizes)
    time_panel.set_title("Time")

    draw_panel(memory_panel, rows, topology, benchmarks, "bytes", None,
               "Memory for one run", sizes)
    memory_panel.set_title("Memory")

    handles, labels = time_panel.get_legend_handles_labels()
    figure.legend(handles, labels, loc="upper center",
                  bbox_to_anchor=(0.5, 0.91), ncol=min(len(labels), 2),
                  fontsize=8, frameon=False)

    figure.suptitle(title, y=0.99)
    figure.tight_layout(rect=(0, 0.16, 1, 0.87))
    figure.text(0.02, 0.02, footnote, fontsize=7, va="bottom", color="#444444")
    figure.savefig(path, dpi=200)
    plt.close(figure)
    print("wrote", path)


def reuse_saving(rows, topology):
    """How much cheaper reusing a saved answer is, written out for the caption."""
    worked = [p for p in series(rows, "coldEndToEnd", topology) if p["time"]]
    saved = [p for p in series(rows, "cachedProjectDuration", topology)
             if p["time"]]
    if not worked or not saved or saved[-1]["time"] <= 0:
        return ""

    times = worked[-1]["time"] / saved[-1]["time"]
    scale = ("more than a million times" if times >= 1_000_000
             else f"about {times:,.0f} times")
    return (
        f"At {worked[-1]['activities']:,} activities, working the schedule out "
        f"takes {worked[-1]['time']:,.1f} against {saved[-1]['time']:,.5f} for "
        f"reusing it, so reusing is {scale} cheaper.\n"
    )


def exponent_note(points, value_key):
    """The measured growth, written next to the name in the legend."""
    slope = growth_exponent(points, value_key)
    if slope is None:
        return ""
    if abs(slope) < 0.15:
        return "  [flat]"
    return f"  [n^{slope:.2f}]"


def growth_exponent(points, value_key):
    """
    How steeply the cost grows, as the power of the plan size.

    Fits a straight line through the points on logarithmic axes, by plain least
    squares. 1.0 means doubling the plan doubles the cost, and 0.0 means the
    plan size makes no difference at all.
    """
    xs = [math.log10(point["activities"]) for point in points]
    ys = [math.log10(point[value_key]) for point in points]
    if len(xs) < 2:
        return None

    mean_x = sum(xs) / len(xs)
    mean_y = sum(ys) / len(ys)
    spread = sum((x - mean_x) ** 2 for x in xs)
    if spread == 0:
        return None
    return sum((x - mean_x) * (y - mean_y) for x, y in zip(xs, ys)) / spread


def value_at(rows, benchmark, topology, size, key="time"):
    match = [
        row for row in series(rows, benchmark, topology)
        if row["activities"] == size
    ]
    return match[0][key] if match else None


def composition_chart(rows, topology, unit, path):
    """
    What share of the total time each step takes, at every plan size.

    The three shares are worked out by subtraction: checking is the check
    benchmark minus reading, and scheduling is the schedule benchmark minus
    reading. The subtraction also cancels the rebuild that those two
    benchmarks include, so these shares describe the steps better than the
    raw numbers do.

    Bars are used here rather than lines because the scale is a plain
    percentage, and bar lengths can only be compared honestly on a plain
    scale.
    """
    sizes = sorted({row["activities"] for row in rows})
    parts = {
        "reading the text": [],
        "checking for mistakes": [],
        "working out the schedule": [],
    }
    absolute = {key: [] for key in parts}
    kept = []
    totals = []

    for size in sizes:
        read = value_at(rows, "parseOnly", topology, size)
        check = value_at(rows, "semanticValidation", topology, size)
        schedule = value_at(rows, "firstCpmEvaluation", topology, size)
        if read is None or check is None or schedule is None:
            continue

        pieces = [read, max(0.0, check - read), max(0.0, schedule - read)]
        total = sum(pieces)
        if total <= 0:
            continue

        kept.append(size)
        totals.append(total)
        for key, piece in zip(parts, pieces):
            parts[key].append(100.0 * piece / total)
            absolute[key].append(piece)

    if not kept:
        return

    figure, axes = plt.subplots(figsize=(9.5, 5))
    positions = list(range(len(kept)))
    bottom = [0.0] * len(kept)

    for label, values in parts.items():
        axes.bar(positions, values, bottom=bottom, label=label, width=0.6)
        for position, value, base, exact in zip(
                positions, values, bottom, absolute[label]):
            if value >= 7:
                axes.text(position, base + value / 2,
                          f"{exact:,.3f}", ha="center", va="center",
                          fontsize=7, color="white")
        bottom = [b + v for b, v in zip(bottom, values)]

    for position, total in zip(positions, totals):
        axes.text(position, 101, f"{total:,.3f}", ha="center", va="bottom",
                  fontsize=8, fontweight="bold")

    axes.set_xticks(positions)
    axes.set_xticklabels([f"{size:,}" for size in kept])
    axes.set_xlabel("Number of activities in the plan")
    axes.set_ylabel("Share of the total time (%)")
    axes.set_ylim(0, 108)
    axes.set_title(
        "What the time is spent on, as plans get bigger\n"
        f"({SHAPES.get(topology, topology)})"
    )
    # Outside the bars: inside, it covers whichever bar it sits on.
    axes.legend(fontsize=8, loc="upper left", bbox_to_anchor=(1.01, 1.0))
    figure.tight_layout(rect=(0, 0.12, 1, 1))
    figure.text(
        0.02, 0.02,
        "Bar heights are shares, so the sizes stay comparable. The numbers "
        f"printed on the bars are the real measured times in {unit},\n"
        "and the bold number above each bar is the total. Bands of the same "
        "width across the chart mean the balance between the steps\nstays the "
        "same as plans grow; a band that widens is a step taking over as "
        "plans get bigger.",
        fontsize=7, va="bottom", color="#444444",
    )
    figure.savefig(path, dpi=200)
    plt.close(figure)
    print("wrote", path)


def numbers_table(rows, unit, path):
    """The raw numbers, so any value can simply be looked up."""
    sizes = sorted({row["activities"] for row in rows})
    lines = ["# Benchmark numbers\n"]

    for topology in sorted({row["topology"] for row in rows}):
        lines.append(f"\n## {topology} - {SHAPES.get(topology, topology)}\n")

        for key, heading, render in (
            ("time", f"Time, in {unit}", format_time),
            ("bytes", "Memory", byte_size),
        ):
            lines.append(f"\n### {heading}\n")
            lines.append("| step | " + " | ".join(f"{s:,}" for s in sizes) + " |")
            lines.append("|---" * (len(sizes) + 1) + "|")
            for name, label, _ in STEPS:
                cells = []
                for size in sizes:
                    value = value_at(rows, name, topology, size, key)
                    cells.append("-" if value is None else render(value))
                lines.append(f"| {label} | " + " | ".join(cells) + " |")

    path.write_text("\n".join(lines) + "\n")
    print("wrote", path)


def format_time(value):
    """Three decimals would round a reused lookup down to 0.001."""
    return f"{value:,.5f}" if value < 0.01 else f"{value:,.3f}"


def unit_name(jmh_unit):
    """JMH writes units like 'us/op'. Spell them out for the axis label."""
    return {
        "ns/op": "nanoseconds",
        "us/op": "microseconds",
        "ms/op": "milliseconds",
        "s/op": "seconds",
    }.get(jmh_unit, jmh_unit)


def main():
    path = results_file()
    print("reading", path)
    rows = read_rows(path)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    unit = unit_name(rows[0]["time_unit"])

    work = [step for step in STEPS if step[0] != "cachedProjectDuration"]
    reuse = [step for step in STEPS if step[0] == "cachedProjectDuration"]

    for topology in sorted({row["topology"] for row in rows}):
        shape = SHAPES.get(topology, topology)

        growth_figure(
            rows, topology, work, unit,
            f"Doing the work: time and memory, {shape}",
            GROWTH_NOTE + "\n" + NOTE,
            OUTPUT / f"growth-working-{topology}.png",
        )
        growth_figure(
            rows, topology, reuse, unit,
            f"Reusing a saved schedule: time and memory, {shape}",
            reuse_saving(rows, topology) + REUSE_NOTE,
            OUTPUT / f"growth-reusing-{topology}.png",
        )
        composition_chart(
            rows, topology, unit, OUTPUT / f"composition-{topology}.png"
        )

    numbers_table(rows, unit, OUTPUT / "benchmark-numbers.md")


if __name__ == "__main__":
    main()

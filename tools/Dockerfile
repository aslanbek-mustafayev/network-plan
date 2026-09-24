# Draws the benchmark charts, so that no Python has to be installed locally.
# The image only provides Python and matplotlib. The script itself is read from
# the repository, which gets mounted at /work, so editing the script does not
# need the image to be rebuilt. See the README for the commands.
FROM python:3.12-slim

RUN pip install --no-cache-dir matplotlib

WORKDIR /work

ENTRYPOINT ["python", "tools/plot_benchmarks.py"]

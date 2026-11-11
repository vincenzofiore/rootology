"""Mean land elevation and land fraction per AgERA5 cell, from the Copernicus DEM GLO-90 (#62).

Offline tool, not part of the app. AgERA5 has no elevation field and keeps values over the sea, so the
climate profile needs, for every 0.1 degree cell, the mean elevation of its land (to correct a garden's
frost dates for its own altitude) and how much of it is land (to drop sea cells).

The DEM comes as 1x1 degree Cloud Optimized GeoTIFF tiles from the public bucket copernicus-dem-90m
(about 90 m; no tile where there is only sea). Each tile is downloaded, every pixel is assigned to the
AgERA5 cell whose centre is nearest (cells are centred on multiples of 0.1 degree), and only the sums are
kept; the tile is then deleted. In the DEM the sea is flattened to exactly 0 m, so a pixel at 0 m is
counted as water; lakes have their own surface elevation and count as land. A cell's land fraction is its
land pixels over the pixels the cell would have if fully covered (columns per degree shrink with latitude,
as the DEM's own grid does).

Produced using Copernicus WorldDEM-90 (c) DLR e.V. 2010-2014 and (c) Airbus Defence and Space GmbH 2014-2018
provided under COPERNICUS by the European Union and ESA; all rights reserved.

Usage:
  .venv/bin/python -I dem_cells.py tiles [--area N W S E] [--work DIR]
  .venv/bin/python -I dem_cells.py combine --out dem-cells.csv [--area N W S E] [--work DIR]
"""

import argparse
import datetime
import math
import sys
import urllib.error
import urllib.request
from pathlib import Path

import numpy as np
import rasterio

BUCKET = "https://copernicus-dem-90m.s3.amazonaws.com"
EUROPE = (72.0, -25.0, 34.0, 45.0)  # north, west, south, east, as in agera5_frost.py
ROWS_PER_DEGREE = 1200  # 3 arc seconds


def columns_per_degree(lat):
    """The DEM's own longitude spacing, by latitude band (Copernicus DEM product handbook)."""
    a = abs(lat)
    return 1200 if a < 50 else 800 if a < 60 else 600 if a < 70 else 400 if a < 80 else 240 if a < 85 else 120


def tile_name(lat, lon):
    ns, ew = ("N" if lat >= 0 else "S"), ("E" if lon >= 0 else "W")
    return f"Copernicus_DSM_COG_30_{ns}{abs(lat):02d}_00_{ew}{abs(lon):03d}_00_DEM"


def tiles_in(area):
    """The tiles under the area's cells (a cell reaches 0.05 degrees beyond its centre)."""
    north, west, south, east = area
    return [(lat, lon) for lat in range(math.floor(south - 0.05), math.ceil(north + 0.05))
            for lon in range(math.floor(west - 0.05), math.ceil(east + 0.05))]


def tile_sums(lat, lon, work, available):
    """Sums of one tile per cell: cell indices (round(lat*10), round(lon*10)), land pixels, their elevation."""
    name = tile_name(lat, lon)
    target = work / "dem" / f"{name}.npz"
    if target.exists():
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    if name not in available:  # only sea: nothing to add
        np.savez(target, lat=np.array([], np.int32), lon=np.array([], np.int32),
                 land=np.array([], np.int64), elevation=np.array([], np.float64))
        return target
    tif = work / "dem" / f"{name}.tif"
    partial = tif.with_suffix(".part")
    urllib.request.urlretrieve(f"{BUCKET}/{name}/{name}.tif", partial)
    partial.rename(tif)
    with rasterio.open(tif) as src:
        z = src.read(1).astype(np.float64)
        t = src.transform
    rows, cols = z.shape
    pixel_lat = t.f + (np.arange(rows) + 0.5) * t.e
    pixel_lon = t.c + (np.arange(cols) + 0.5) * t.a
    cell_lat = np.rint(pixel_lat * 10).astype(np.int32)
    cell_lon = np.rint(pixel_lon * 10).astype(np.int32)
    land = (z != 0) & np.isfinite(z)
    lat_idx = np.broadcast_to(cell_lat[:, None], z.shape)[land]
    lon_idx = np.broadcast_to(cell_lon[None, :], z.shape)[land]
    keys, inverse = np.unique(np.stack([lat_idx, lon_idx]), axis=1, return_inverse=True)
    inverse = inverse.ravel()
    np.savez(target, lat=keys[0], lon=keys[1],
             land=np.bincount(inverse, minlength=keys.shape[1]),
             elevation=np.bincount(inverse, weights=z[land], minlength=keys.shape[1]))
    tif.unlink()
    return target


def expected_pixels(cell_lat):
    """Pixels a fully covered cell has: 120 rows, each with the band's columns per 0.1 degree."""
    rows = (cell_lat / 10 - 0.05) + (np.arange(120) + 0.5) / ROWS_PER_DEGREE
    return sum(columns_per_degree(r) / 10 for r in rows)


def combine(area, work, out):
    north, west, south, east = area
    land, elevation = {}, {}
    for lat, lon in tiles_in(area):
        f = work / "dem" / f"{tile_name(lat, lon)}.npz"
        if not f.exists():
            sys.exit(f"missing tile sums: {f.name} (run the tiles command)")
        with np.load(f) as d:
            for key in zip(d["lat"].tolist(), d["lon"].tolist(), d["land"].tolist(), d["elevation"].tolist()):
                cell = key[:2]
                land[cell] = land.get(cell, 0) + key[2]
                elevation[cell] = elevation.get(cell, 0.0) + key[3]
    with open(out, "w") as f:
        f.write("# Copernicus DEM GLO-90 per AgERA5 cell: mean land elevation (m) and land fraction. Produced using "
                "Copernicus WorldDEM-90 (c) DLR e.V. 2010-2014 and (c) Airbus Defence and Space GmbH 2014-2018 "
                "provided under COPERNICUS by the European Union and ESA; all rights reserved.\n")
        f.write("lat,lon,elevation_m,land_fraction\n")
        for i in range(round(north * 10) - 1, round(south * 10) - 1, -1):  # same cells as agera5_frost.py
            expected = expected_pixels(i)
            for j in range(round(west * 10) + 1, round(east * 10) + 1):
                n = land.get((i, j), 0)
                mean = f"{elevation[(i, j)] / n:.0f}" if n else ""
                f.write(f"{i / 10:.1f},{j / 10:.1f},{mean},{min(n / expected, 1.0):.2f}\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["tiles", "combine"])
    parser.add_argument("--area", type=float, nargs=4, default=EUROPE, metavar=("N", "W", "S", "E"))
    parser.add_argument("--work", type=Path, default=Path(__file__).parent / "work")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    area = tuple(args.area)
    if args.command == "tiles":
        with urllib.request.urlopen(f"{BUCKET}/tileList.txt") as r:
            available = set(r.read().decode().split())
        todo = tiles_in(area)
        for n, (lat, lon) in enumerate(todo, 1):
            for attempt in range(5):
                try:
                    tile_sums(lat, lon, args.work, available)
                    break
                except (urllib.error.URLError, OSError) as e:
                    print(f"{tile_name(lat, lon)}: {type(e).__name__}, retrying", flush=True)
            else:
                sys.exit(f"{tile_name(lat, lon)}: giving up")
            if n % 50 == 0:
                print(f"{datetime.datetime.now():%H:%M} {n}/{len(todo)} tiles", flush=True)
    else:
        if not args.out:
            sys.exit("--out is required")
        combine(area, args.work, args.out)


if __name__ == "__main__":
    main()

"""Frost and growing season indices per AgERA5 cell, for the climate profile of a garden's site (#62).

Offline tool, not part of the app. For every year it downloads AgERA5 v2.0 daily 2 m minimum and mean
temperature month by month (global files: there is no area parameter), keeps only the chosen area,
computes the year's indices and deletes the daily files. Then it combines the years into one CSV.

Indices per year (TN = daily minimum, TG = daily mean, both in degrees C):
- fd: frost days, TN < 0 (ETCCDI FD);
- gsl: growing season length (ETCCDI GSL): from the first span of 6 days with TG > 5 to the first span
  of 6 days with TG < 5 after 1 July (northern hemisphere only);
- last_frost: last day of year before 1 July with TN < 0 (0 when there was none);
- first_frost: first day of year from 1 July with TN < 0 (367 when there was none).

Combined over the years: mean fd and gsl, the median last and first frost, the last frost date
exceeded in 1 year out of 5 (80th percentile) and the first frost date preceded in 1 year out of 5
(20th percentile), and the number of years with any frost. Dates are written as MM-DD of a non-leap
year; a date that falls in a year without frost is left empty.

The CDS key is read by cdsapi from ~/.cdsapirc. Every step can be interrupted and resumed.

With --dem (the CSV of dem_cells.py) combine keeps only cells with at least --min-land of land (AgERA5
has values over the sea too) and adds each cell's mean land elevation, for the altitude correction.

Usage:
  .venv/bin/python -I agera5_frost.py years 1991 2020 [--area N W S E] [--work DIR]
  .venv/bin/python -I agera5_frost.py combine 1991 2020 --out cells.csv [--dem dem-cells.csv] [--work DIR]
"""

import argparse
import calendar
import datetime
import shutil
import sys
import zipfile
from pathlib import Path

import numpy as np
import xarray as xr

DATASET = "sis-agrometeorological-indicators"
EUROPE = (72.0, -25.0, 34.0, 45.0)  # north, west, south, east
JULY_FIRST = 182  # day of year of 1 July in a non-leap year


def download_month(year, month, area, work):
    """The month's daily TN and TG for the area, cached as one small NetCDF file."""
    target = work / "months" / f"{year}-{month:02d}.nc"
    if target.exists():
        return target
    import cdsapi  # only needed when downloading

    raw = work / "raw" / f"{year}-{month:02d}"
    raw.mkdir(parents=True, exist_ok=True)
    archive = raw / "data.zip"
    days = [f"{d:02d}" for d in range(1, calendar.monthrange(year, month)[1] + 1)]
    if not archive.exists():
        partial_zip = raw / "data.part"
        cdsapi.Client(quiet=True, progress=False).retrieve(DATASET, {
            "variable": "2m_temperature",
            "statistic": ["24_hour_minimum", "24_hour_mean"],
            "year": [str(year)], "month": [f"{month:02d}"], "day": days,
            "version": "2_0",
        }).download(str(partial_zip))
        partial_zip.rename(archive)
    with zipfile.ZipFile(archive) as z:
        z.extractall(raw)
    names = {"Temperature_Air_2m_Min_24h": "tn", "Temperature_Air_2m_Mean_24h": "tg"}
    cut = xr.merge([xr.concat([daily(f, source, area) for f in sorted(raw.glob("*.nc")) if source in read_vars(f)],
                              dim="time").rename(target_name)
                    for source, target_name in names.items()])
    if cut.sizes["time"] != len(days):
        sys.exit(f"{year}-{month:02d}: {cut.sizes['time']} days instead of {len(days)}")
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_suffix(".part")
    cut.to_netcdf(partial, encoding={v: {"zlib": True, "complevel": 4} for v in ("tn", "tg")})
    partial.rename(target)
    shutil.rmtree(raw)
    return target


def read_vars(path):
    with xr.open_dataset(path) as ds:
        return set(ds.data_vars)


def daily(path, variable, area):
    """One day of one variable, cut to the area, in degrees C."""
    north, west, south, east = area
    with xr.open_dataset(path) as ds:
        return (ds[variable].sel(lat=slice(north, south), lon=slice(west, east)) - 273.15).astype("float32").load()


def first_span(mask, length=6):
    """Index of the first day starting `length` consecutive True values along axis 0, or -1."""
    run = np.zeros(mask.shape[1:], dtype=np.int16)
    start = np.full(mask.shape[1:], -1, dtype=np.int16)
    for day in range(mask.shape[0]):
        run = np.where(mask[day], run + 1, 0)
        found = (run == length) & (start < 0)
        start[found] = day - length + 1
    return start


def year_indices(year, area, work):
    target = work / "years" / f"{year}.nc"
    if target.exists():
        return target
    months = [download_month(year, m, area, work) for m in range(1, 13)]
    ds = xr.concat([xr.load_dataset(m) for m in months], dim="time")
    tn = ds["tn"].values
    tg = ds["tg"].values
    lat, lon = ds["lat"].values, ds["lon"].values
    july = JULY_FIRST - 1 + (1 if calendar.isleap(year) else 0)  # index of 1 July
    frost = tn < 0
    doy = np.arange(1, tn.shape[0] + 1)[:, None, None]
    if calendar.isleap(year):  # count days as in a non-leap year, 29 February as 28
        doy = np.where(doy > 59, doy - 1, doy)
    last = np.where(frost[:july], doy[:july], 0).max(axis=0)
    first = np.where(frost[july:], doy[july:], 367).min(axis=0)
    start = first_span(tg > 5)
    end_after_july = first_span(tg[july:] < 5)
    end = np.where(end_after_july >= 0, end_after_july + july, tg.shape[0])
    gsl = np.where(start >= 0, np.maximum(end - start, 0), 0)
    land = ~np.isnan(tn).all(axis=0)
    out = xr.Dataset(
        {name: (("lat", "lon"), np.where(land, values, np.nan).astype("float32"))
         for name, values in {"fd": frost.sum(axis=0), "gsl": gsl, "last_frost": last, "first_frost": first}.items()},
        coords={"lat": lat, "lon": lon})
    target.parent.mkdir(parents=True, exist_ok=True)
    out.to_netcdf(target)
    for month in months:
        month.unlink()
    return target


def month_day(day_of_year):
    if np.isnan(day_of_year) or day_of_year < 1 or day_of_year > 365:
        return ""
    return (datetime.date(2001, 1, 1) + datetime.timedelta(days=int(round(day_of_year)) - 1)).strftime("%m-%d")


def read_dem(path):
    """(lat, lon) as in the CSV -> (elevation in m or None, land fraction)."""
    cells = {}
    with open(path) as f:
        for line in f:
            if line.startswith("#") or line.startswith("lat,"):
                continue
            lat, lon, elevation, land = line.rstrip("\n").split(",")
            cells[(lat, lon)] = (elevation or None, float(land))
    return cells


def combine(first_year, last_year, work, out, dem=None, min_land=0.0):
    files = [work / "years" / f"{y}.nc" for y in range(first_year, last_year + 1)]
    missing = [f.name for f in files if not f.exists()]
    if missing:
        sys.exit(f"missing years: {', '.join(missing)}")
    ds = xr.concat([xr.load_dataset(f) for f in files], dim="year")
    years = ds.sizes["year"]
    last = ds["last_frost"].values
    first = ds["first_frost"].values
    table = {
        "fd": ds["fd"].mean("year").values,
        "gsl": ds["gsl"].mean("year").values,
        "last_frost_p50": np.percentile(last, 50, axis=0, method="lower"),
        "last_frost_p80": np.percentile(last, 80, axis=0, method="higher"),
        "first_frost_p50": np.percentile(first, 50, axis=0, method="higher"),
        "first_frost_p20": np.percentile(first, 20, axis=0, method="lower"),
        "frost_years": ((last > 0) | (first < 367)).sum(axis=0),
    }
    with open(out, "w") as f:
        f.write(f"# AgERA5 v2.0 {first_year}-{last_year}, {years} years. "
                "Contains modified Copernicus Climate Change Service information.")
        if dem:
            f.write(f" Land cells (at least {min_land:.0%} land) and mean land elevation from the Copernicus DEM "
                    "GLO-90: produced using Copernicus WorldDEM-90 (c) DLR e.V. 2010-2014 and (c) Airbus Defence and "
                    "Space GmbH 2014-2018 provided under COPERNICUS by the European Union and ESA.")
        f.write("\nlat,lon," + ("elevation_m," if dem else "")
                + "fd,gsl,last_frost_p50,last_frost_p80,first_frost_p50,first_frost_p20,frost_years\n")
        lats, lons = ds["lat"].values, ds["lon"].values
        for i, lat in enumerate(lats):
            for j, lon in enumerate(lons):
                if np.isnan(table["fd"][i, j]):
                    continue
                key = (f"{lat:.1f}", f"{lon:.1f}")
                elevation = ""
                if dem:
                    elevation, land = dem.get(key, (None, 0.0))
                    if land < min_land or elevation is None:
                        continue
                    elevation += ","
                f.write(f"{key[0]},{key[1]},{elevation}{table['fd'][i, j]:.0f},{table['gsl'][i, j]:.0f},"
                        f"{month_day(table['last_frost_p50'][i, j])},{month_day(table['last_frost_p80'][i, j])},"
                        f"{month_day(table['first_frost_p50'][i, j])},{month_day(table['first_frost_p20'][i, j])},"
                        f"{int(table['frost_years'][i, j])}\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["years", "combine"])
    parser.add_argument("first_year", type=int)
    parser.add_argument("last_year", type=int)
    parser.add_argument("--area", type=float, nargs=4, default=EUROPE, metavar=("N", "W", "S", "E"))
    parser.add_argument("--work", type=Path, default=Path(__file__).parent / "work")
    parser.add_argument("--out", type=Path)
    parser.add_argument("--dem", type=Path, help="CSV of dem_cells.py: keep land cells and add their elevation")
    parser.add_argument("--min-land", type=float, default=0.25, help="least land fraction of a kept cell")
    args = parser.parse_args()
    if args.command == "years":
        for year in range(args.first_year, args.last_year + 1):
            print(f"{datetime.datetime.now():%H:%M} year {year}", flush=True)
            year_indices(year, tuple(args.area), args.work)
    else:
        if not args.out:
            sys.exit("--out is required")
        combine(args.first_year, args.last_year, args.work, args.out,
                read_dem(args.dem) if args.dem else None, args.min_land)


if __name__ == "__main__":
    main()

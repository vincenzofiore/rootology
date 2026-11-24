"""Offline tests for agera5_frost.py (#147): no network, no CDS key, synthetic arrays only.

Run from the worktree root with the tool's virtual environment:
  tools/climate/.venv/bin/python -I -m unittest discover -s tools/climate -p 'test_*.py'
"""

import io
import tempfile
import unittest
from contextlib import redirect_stderr
from pathlib import Path

import numpy as np
import xarray as xr

import agera5_frost

LAT = (45.0, 45.1)
LON = (9.0, 9.1)


def tiny_days(days, values):
    """Daily TN of a winter: (days, 2, 2), every cell set to `values`."""
    return np.full((days, 2, 2), values, dtype="float32")


def write_year(work, year, fd=30.0):
    (work / "years").mkdir(parents=True, exist_ok=True)
    ds = xr.Dataset(
        {
            "fd": (("lat", "lon"), np.full((2, 2), fd, dtype="float32")),
            "gsl": (("lat", "lon"), np.full((2, 2), 250.0, dtype="float32")),
            "last_frost": (("lat", "lon"), np.full((2, 2), 60.0, dtype="float32")),
            "first_frost": (("lat", "lon"), np.full((2, 2), 300.0, dtype="float32")),
        },
        coords={"lat": np.array(LAT), "lon": np.array(LON)})
    ds.to_netcdf(work / "years" / f"{year}.nc")


def write_winter(work, winter, values):
    """One winter file: `values` is the (2, 2) winter_min of the cells."""
    (work / "winters").mkdir(parents=True, exist_ok=True)
    ds = xr.Dataset({"winter_min": (("lat", "lon"), np.array(values, dtype="float32"))},
                    coords={"lat": np.array(LAT), "lon": np.array(LON)})
    ds.to_netcdf(work / "winters" / f"{winter}.nc")


def combine(first, last, work, out, winters=False):
    agera5_frost.combine(first, last, work, out, None, 0.0, winters)


class WinterMinimumTest(unittest.TestCase):
    def test_lowest_day_across_the_three_months(self):
        december, january, february = tiny_days(31, 2.0), tiny_days(31, -4.5), tiny_days(28, 0.5)
        lowest = agera5_frost.winter_minimum(np.concatenate([december, january, february]))
        np.testing.assert_allclose(lowest, np.full((2, 2), -4.5, dtype="float32"))

    def test_a_cell_without_any_value_stays_nan(self):
        days = tiny_days(5, 1.0)
        days[:, 1, 1] = np.nan
        lowest = agera5_frost.winter_minimum(days)
        self.assertEqual(lowest[0, 0], 1.0)
        self.assertTrue(np.isnan(lowest[1, 1]))


class WinterP20Test(unittest.TestCase):
    def test_over_ten_winters_it_is_the_second_coldest(self):
        stack = np.arange(10, dtype="float32").reshape(10, 1, 1)  # 0..9, colder first
        self.assertEqual(agera5_frost.winter_p20(stack)[0, 0], 1.0)

    def test_a_cell_without_any_value_stays_nan(self):
        stack = np.full((3, 2, 2), -2.0, dtype="float32")
        stack[:, 0, 1] = np.nan
        p20 = agera5_frost.winter_p20(stack)
        self.assertEqual(p20[0, 0], -2.0)
        self.assertTrue(np.isnan(p20[0, 1]))


class WinterFileTest(unittest.TestCase):
    def test_writes_the_winter_and_drops_the_months(self):
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            (work / "months").mkdir()
            for name, days, values in (("1995-12", 31, 1.0), ("1996-01", 31, -6.0), ("1996-02", 29, 2.0)):
                tn = tiny_days(days, values)
                xr.Dataset({"tn": (("time", "lat", "lon"), tn)},
                           coords={"time": np.arange(days), "lat": np.array(LAT), "lon": np.array(LON)}
                           ).to_netcdf(work / "months" / f"{name}.nc")
            agera5_frost.winter_minimum_file(1996, (46.0, 8.0, 44.0, 10.0), work)
            with xr.load_dataset(work / "winters" / "1996.nc") as ds:
                np.testing.assert_allclose(ds["winter_min"].values, np.full((2, 2), -6.0, dtype="float32"))
            self.assertEqual(list((work / "months").iterdir()), [])


class CombineTest(unittest.TestCase):
    def test_without_winters_the_header_is_unchanged(self):
        with tempfile.TemporaryDirectory() as tmp:
            work, out = Path(tmp), Path(tmp) / "cells.csv"
            write_year(work, 2000)
            combine(2000, 2000, work, out)
            header = out.read_text().splitlines()[1]
            self.assertEqual(header, "lat,lon,fd,gsl,last_frost_p50,last_frost_p80,"
                                     "first_frost_p50,first_frost_p20,frost_years")

    def test_with_winters_it_adds_the_column_and_leaves_nan_empty(self):
        with tempfile.TemporaryDirectory() as tmp:
            work, out = Path(tmp), Path(tmp) / "cells.csv"
            for year in range(1996, 2006):
                write_year(work, year)
                write_winter(work, year, [[-1.0, -2.0], [-3.0, np.nan]])
            combine(1996, 2005, work, out, winters=True)
            lines = out.read_text().splitlines()
            self.assertTrue(lines[1].endswith(",winter_min_p20"))
            self.assertTrue(lines[2].endswith(",-1.0"))  # lat 45.0, lon 9.0: one value, repeated every winter
            self.assertTrue(lines[3].endswith(",-2.0"))
            self.assertTrue(lines[4].endswith(",-3.0"))
            self.assertTrue(lines[5].endswith(","))  # lat 45.1, lon 9.1: no value in any winter

    def test_a_missing_winter_stops_with_its_name(self):
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            write_year(work, 2000)
            write_year(work, 2001)
            write_winter(work, 2000, [[-1.0, -1.0], [-1.0, -1.0]])
            with redirect_stderr(io.StringIO()) as err:
                with self.assertRaises(SystemExit) as raised:
                    combine(2000, 2001, work, Path(tmp) / "cells.csv", winters=True)
            self.assertIn("2001", str(raised.exception))
            self.assertIn("missing winter", str(raised.exception))
            self.assertEqual(err.getvalue(), "")


if __name__ == "__main__":
    unittest.main()
/**
 * Local weather (#18): the public forecast of MET Norway (Locationforecast 2.0, CC BY 4.0, "Data from
 * MET Norway") for a garden's coarse location, cached in Postgres, and two explicit rules on it
 * (frost-risk-v1, smith-period-v1). Forecast hours are kept after they pass, as an estimate of the
 * recent past until real observations arrive (#21). Never blocks an answer: when the service fails,
 * a stored forecast up to a day old is used, marked stale, and otherwise there is no weather.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Weather")
package app.rootology.weather;

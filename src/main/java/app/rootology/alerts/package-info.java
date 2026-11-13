/**
 * Weather alerts (#37): every morning, for each garden with coordinates, the forecast (#18) and the explicit
 * rules frost-risk-v1 and smith-period-v1 are applied to what grows there, and an alert goes to the members
 * of its space through the notifications (#26), once per garden, rule and event.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Weather Alerts",
        allowedDependencies = { "garden", "weather", "facts", "notifications" }
)
package app.rootology.alerts;

package app.rootology.garden;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The gardens of a person's spaces, for export and account deletion (#40, docs/privacy.md). Gardens belong
 * to a space and go with it; a plot keeps the login of whoever declared its sun (#89), which is replaced
 * when that person deletes the account.
 */
@Component
public class GardenPersonalData {

    private final JdbcClient jdbc;

    GardenPersonalData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> export(Collection<Long> spaces) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (spaces.isEmpty()) {
            return data;
        }
        data.put("gardens", jdbc.sql("""
                        select id, space_id, name, town, country_code, latitude, longitude, elevation_m, created_at
                        from garden where space_id in (:spaces) order by id""")
                .param("spaces", spaces).query().listOfRows());
        data.put("plots", jdbc.sql("""
                        select p.id, p.garden_id, p.name, p.kind, p.soil, p.area_m2,
                               p.orientation, p.direct_sun, p.wind, p.floor,
                               p.sun_hours_summer, p.sun_hours_winter, p.sun_declared_by, p.sun_declared_at
                        from plot p join garden g on g.id = p.garden_id where g.space_id in (:spaces) order by p.id""")
                .param("spaces", spaces).query().listOfRows());
        data.put("plantings", jdbc.sql("""
                        select t.id, t.plot_id, t.crop_id, t.method, t.planted_on, t.ended_on, t.notes
                        from planting t join plot p on p.id = t.plot_id join garden g on g.id = p.garden_id
                        where g.space_id in (:spaces) order by t.id""")
                .param("spaces", spaces).query().listOfRows());
        return data;
    }

    /** Who declared a plot's sun becomes the replacement name in the gardens that stay (#89, #40). */
    public void forget(String login, Collection<Long> spaces, String replacement) {
        if (spaces.isEmpty()) {
            jdbc.sql("update plot set sun_declared_by = :r where sun_declared_by = :login")
                    .param("r", replacement).param("login", login).update();
        } else {
            jdbc.sql("""
                            update plot set sun_declared_by = :r
                            where sun_declared_by = :login
                              and garden_id not in (select id from garden where space_id in (:spaces))""")
                    .param("r", replacement).param("login", login).param("spaces", spaces).update();
        }
    }

    /** Plots and plantings follow their garden (on delete cascade). */
    public void deleteSpaces(Collection<Long> spaces) {
        if (!spaces.isEmpty()) {
            jdbc.sql("delete from garden where space_id in (:spaces)").param("spaces", spaces).update();
        }
    }
}

package Cliente_Servidor.V1.Config;

import Cliente_Servidor.V1.Util.ConsolaUtil;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Component
public class MySQLInitializer {

    private final DataSource dataSource;

    public MySQLInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void configurarMySQL() {
        try (Connection con = dataSource.getConnection();
             Statement stmt = con.createStatement()) {

            // 256 MB para soportar chunks grandes + overhead de encriptación
            stmt.execute("SET GLOBAL max_allowed_packet = 268435456");

            // Optimizaciones para escritura paralela de BLOBs
            try {
                stmt.execute("SET GLOBAL innodb_flush_log_at_trx_commit = 2");
                stmt.execute("SET GLOBAL innodb_buffer_pool_size = 268435456");
            } catch (Exception ignored) {
                // Estas variables pueden requerir reinicio de MySQL
            }

            // Reiniciar pool para que nuevas conexiones usen el valor actualizado
            if (dataSource instanceof HikariDataSource hikari) {
                hikari.getHikariPoolMXBean().softEvictConnections();
            }

            // Verificar que se aplicó
            ResultSet rs = stmt.executeQuery("SELECT @@GLOBAL.max_allowed_packet");
            if (rs.next()) {
                long valor = rs.getLong(1);
                ConsolaUtil.ok("MySQL max_allowed_packet = " + (valor / 1024 / 1024) + " MB");
            }

            ConsolaUtil.ok("MySQL optimizado para escritura paralela");

        } catch (Exception e) {
            ConsolaUtil.warn("No se pudo configurar max_allowed_packet: " + e.getMessage());
            ConsolaUtil.warn("Ejecuta manualmente: SET GLOBAL max_allowed_packet = 268435456");
        }
    }
}

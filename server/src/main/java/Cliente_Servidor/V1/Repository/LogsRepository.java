/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Cliente_Servidor.V1.Repository;

import Cliente_Servidor.V1.Modelos.Logs;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author crism
 */
public interface LogsRepository extends JpaRepository<Logs, Long> {
    
}

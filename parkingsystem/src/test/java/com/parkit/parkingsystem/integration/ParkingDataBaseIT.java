package com.parkit.parkingsystem.integration;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import com.parkit.parkingsystem.model.Ticket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.lang.Thread;
import java.math.BigDecimal;
import java.math.RoundingMode;

@ExtendWith(MockitoExtension.class)
public class ParkingDataBaseIT {

    private static DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
    private static ParkingSpotDAO parkingSpotDAO;
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;

    @Mock
    private static InputReaderUtil inputReaderUtil;

    @BeforeAll
    private static void setUp() throws Exception{
        parkingSpotDAO = new ParkingSpotDAO();
        parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;
        ticketDAO = new TicketDAO();
        ticketDAO.dataBaseConfig = dataBaseTestConfig;
        dataBasePrepareService = new DataBasePrepareService();
    }

    @BeforeEach
    private void setUpPerTest() throws Exception {
        lenient().when(inputReaderUtil.readSelection()).thenReturn(1);
        lenient().when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
        dataBasePrepareService.clearDataBaseEntries();
    }

    @AfterAll
    private static void tearDown(){

    }

    @Test
    public void testParkingACar(){
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processIncomingVehicle();
        //TODO: check that a ticket is actualy saved in DB and Parking table is updated with availability
        assertNotNull(ticketDAO.getTicket("ABCDEF"));
        assertEquals("ABCDEF", ticketDAO.getTicket("ABCDEF").getVehicleRegNumber());
        assertEquals(0, ticketDAO.getTicket("ABCDEF").getPrice());
        assertNull(ticketDAO.getTicket("ABCDEF").getOutTime());
        assertFalse(ticketDAO.getTicket("ABCDEF").getParkingSpot().isAvailable());
    }

    @Test
    public void testParkingLotExit() throws Exception {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processIncomingVehicle();
        Thread.sleep(1000);
        parkingService.processExitingVehicle();
        Thread.sleep(1000);
        //TODO: check that the fare generated and out time are populated correctly in the database
        assertEquals(0, ticketDAO.getTicket("ABCDEF").getPrice());
        assertNotNull(ticketDAO.getTicket("ABCDEF").getOutTime());
    }

    @Test
    public void testParkingLotExitRecurringUser() throws Exception {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processIncomingVehicle();
        Ticket ticket1 = ticketDAO.getTicket("ABCDEF");
        ticket1.setInTime(new Date(ticketDAO.getTicket("ABCDEF").getInTime().getTime() - (60*60*1000)));
        ticketDAO.updateTicketIntime(ticket1);
        parkingService.processExitingVehicle();
        parkingService.processIncomingVehicle();
        Ticket ticket2 = ticketDAO.getTicket("ABCDEF");
        ticket2.setInTime(new Date(ticketDAO.getTicket("ABCDEF").getInTime().getTime() - (30*60*1000)));
        ticketDAO.updateTicketIntime(ticket2);
        Thread.sleep(500);
        parkingService.processExitingVehicle();
        BigDecimal expectedPrice = BigDecimal.valueOf((30/(double)60) * Fare.CAR_RATE_PER_HOUR * 0.95).setScale(2, RoundingMode.CEILING);
        BigDecimal ticketPrice = BigDecimal.valueOf(ticketDAO.getTicket("ABCDEF").getPrice()).setScale(2, RoundingMode.CEILING);
        assertEquals(expectedPrice,ticketPrice); 
    }
}

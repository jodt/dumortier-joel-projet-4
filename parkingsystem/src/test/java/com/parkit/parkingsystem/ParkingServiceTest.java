package com.parkit.parkingsystem;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.service.FareCalculatorService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;



@ExtendWith(MockitoExtension.class)
public class ParkingServiceTest {

    private static ParkingService parkingService;
    private final PrintStream standardOut = System.out;
    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();

    @Mock
    private static InputReaderUtil inputReaderUtil;
    @Mock
    private static ParkingSpotDAO parkingSpotDAO;
    @Mock
    private static TicketDAO ticketDAO;
    @Captor
    ArgumentCaptor<Ticket> ticketCaptor;

    @BeforeEach
    private void setUpPerTest() {
        try {
            System.setOut(new PrintStream(outputStreamCaptor));
            lenient().when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");

            ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR,false);
            Ticket ticket = new Ticket();
            ticket.setInTime(new Date(System.currentTimeMillis() - (60*60*1000)));
            ticket.setParkingSpot(parkingSpot);
            ticket.setVehicleRegNumber("ABCDEF");
            lenient().when(ticketDAO.getTicket(anyString())).thenReturn(ticket);
            lenient().when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(true);

            lenient().when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(true);

            parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        } catch (Exception e) {
            e.printStackTrace();
            throw  new RuntimeException("Failed to set up test mock objects");
        }
    }

    @AfterEach
    public void tearDown() {
        System.setOut(standardOut);
    }


    //Tests for processExitingVehicle

    @Test
    public void processExitingVehicleTest() throws Exception {
        when(ticketDAO.isAlreadyInParking(anyString())).thenReturn(true);
        when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(0);

        parkingService.processExitingVehicle();

        verify(inputReaderUtil, Mockito.times(1)).readVehicleRegistrationNumber();
        verify(ticketDAO, Mockito.times(1)).getTicket("ABCDEF");
        verify(ticketDAO, Mockito.times(1)).updateTicket(ticketCaptor.capture());
        verify(parkingSpotDAO, Mockito.times(1)).updateParking(any(ParkingSpot.class));
        verify(ticketDAO, Mockito.times(1)).getNbTicket("ABCDEF");

        Ticket ticketCaptorValue = ticketCaptor.getValue();
        BigDecimal expectedPrice = BigDecimal.valueOf(Fare.CAR_RATE_PER_HOUR).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ticketPrice = BigDecimal.valueOf(ticketCaptorValue.getPrice()).setScale(2, RoundingMode.HALF_UP);


        assertNotNull(ticketCaptorValue.getOutTime());
        assertTrue(ticketCaptorValue.getParkingSpot().isAvailable());
        assertEquals(expectedPrice, ticketPrice);
    }

    @Test
    public void processExitingVehicleTestWithDiscount() throws Exception {
        when(ticketDAO.isAlreadyInParking(anyString())).thenReturn(true);
        when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(5);

        parkingService.processExitingVehicle();

        verify(inputReaderUtil, Mockito.times(1)).readVehicleRegistrationNumber();
        verify(ticketDAO, Mockito.times(1)).getTicket("ABCDEF");
        verify(ticketDAO, Mockito.times(1)).updateTicket(ticketCaptor.capture());
        verify(parkingSpotDAO, Mockito.times(1)).updateParking(any(ParkingSpot.class));
        verify(ticketDAO, Mockito.times(1)).getNbTicket("ABCDEF");

        Ticket ticketCaptorValue = ticketCaptor.getValue();
        BigDecimal expectedPrice = BigDecimal.valueOf(Fare.CAR_RATE_PER_HOUR * 0.95).setScale(2, RoundingMode.CEILING);
        BigDecimal ticketPrice = BigDecimal.valueOf(ticketCaptorValue.getPrice()).setScale(2, RoundingMode.CEILING);

        assertNotNull(ticketCaptorValue.getOutTime());
        assertTrue(ticketCaptorValue.getParkingSpot().isAvailable());
        assertEquals(expectedPrice, ticketPrice);
    }

    @Test
    public void processExitingVehicleTestUnableUpdate() throws Exception {
        when(ticketDAO.isAlreadyInParking(anyString())).thenReturn(true);
        when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(0);
        when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(false);

        parkingService.processExitingVehicle();

        verify(inputReaderUtil, times(1)).readVehicleRegistrationNumber();
        verify(ticketDAO, Mockito.times(1)).getTicket("ABCDEF");
        verify(ticketDAO, Mockito.times(1)).updateTicket(ticketCaptor.capture());
        verify(parkingSpotDAO, Mockito.never()).updateParking(any(ParkingSpot.class));
        verify(ticketDAO, Mockito.times(1)).getNbTicket("ABCDEF");

        Ticket ticketCaptorValue = ticketCaptor.getValue();
        assertFalse(ticketCaptorValue.getParkingSpot().isAvailable());
        assertTrue(outputStreamCaptor.toString().trim().contains("Unable to update ticket information. Error occurred"));
    }

    @Test
    public void processExitingIfVehicleIsNotParked() throws Exception {
        when(ticketDAO.isAlreadyInParking(anyString())).thenReturn(false);

        parkingService.processExitingVehicle();

        verify(inputReaderUtil, times(1)).readVehicleRegistrationNumber();
        verify(ticketDAO, Mockito.never()).getTicket("ABCDEF");
        verify(ticketDAO, Mockito.never()).updateTicket(any(Ticket.class));
        verify(parkingSpotDAO, Mockito.never()).updateParking(any(ParkingSpot.class));
        verify(ticketDAO, Mockito.never()).getNbTicket("ABCDEF");

        assertTrue(outputStreamCaptor.toString().trim().contains("Ce véhicule n'est pas dans le parking"));
    }

    //Tests for processIncomingVehicle
    
    @Test
    public void testProcessIncomingVehicle() throws Exception {
        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(parkingSpotDAO.getNextAvailableSlot(any(ParkingType.class))).thenReturn(1);
        when(ticketDAO.isAlreadyInParking(anyString())).thenReturn(false);
        when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(false);
        when(ticketDAO.saveTicket(any(Ticket.class))).thenReturn(true);
        when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(0);

        parkingService.processIncomingVehicle();

        verify(inputReaderUtil, Mockito.times(1)).readSelection();
        verify(parkingSpotDAO, Mockito.times(1)).getNextAvailableSlot(ParkingType.CAR);
        verify(inputReaderUtil, Mockito.times(1)).readVehicleRegistrationNumber();
        verify(ticketDAO,Mockito.times(1)).isAlreadyInParking("ABCDEF");
        verify(parkingSpotDAO, Mockito.times(1)).updateParking(any(ParkingSpot.class));
        verify(ticketDAO).saveTicket(ticketCaptor.capture());

        Ticket ticketCaptorValue = ticketCaptor.getValue();
        
        assertEquals("ABCDEF", ticketCaptorValue.getVehicleRegNumber());
        assertNotNull(ticketCaptorValue.getInTime());
        assertNull(ticketCaptorValue.getOutTime());
        assertEquals(0, ticketCaptorValue.getPrice());
        assertFalse(outputStreamCaptor.toString().trim().contains("Heureux de vous revoir ! En tant qu’utilisateur régulier de notre parking, vous allez obtenir une remise de 5%"));
    }

    @Test
    public void testProcessIncomingCarVehicleWithDiscount() throws Exception {
        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(parkingSpotDAO.getNextAvailableSlot(any(ParkingType.class))).thenReturn(1);
        when(ticketDAO.isAlreadyInParking(anyString())).thenReturn(false);
        when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(false);
        when(ticketDAO.saveTicket(any(Ticket.class))).thenReturn(true);
        when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(5);

        parkingService.processIncomingVehicle();

        verify(inputReaderUtil, Mockito.times(1)).readSelection();
        verify(parkingSpotDAO, Mockito.times(1)).getNextAvailableSlot(ParkingType.CAR);
        verify(inputReaderUtil, Mockito.times(1)).readVehicleRegistrationNumber();
        verify(ticketDAO,Mockito.times(1)).isAlreadyInParking("ABCDEF");
        verify(parkingSpotDAO, Mockito.times(1)).updateParking(any(ParkingSpot.class));
        verify(ticketDAO).saveTicket(ticketCaptor.capture());

        Ticket ticketCaptorValue = ticketCaptor.getValue();
        
        assertEquals("ABCDEF", ticketCaptorValue.getVehicleRegNumber());
        assertNotNull(ticketCaptorValue.getInTime());
        assertNull(ticketCaptorValue.getOutTime());
        assertEquals(0, ticketCaptorValue.getPrice());
        assertTrue(outputStreamCaptor.toString().trim().contains("Heureux de vous revoir ! En tant qu’utilisateur régulier de notre parking, vous allez obtenir une remise de 5%"));
    }

    @Test
    public void testProcessIncomingBikeVehicleWithoutDiscount() throws Exception {
        when(inputReaderUtil.readSelection()).thenReturn(2);
        when(parkingSpotDAO.getNextAvailableSlot(any(ParkingType.class))).thenReturn(1);
        when(ticketDAO.isAlreadyInParking(anyString())).thenReturn(false);
        when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(false);
        when(ticketDAO.saveTicket(any(Ticket.class))).thenReturn(true);
        when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(0);

        parkingService.processIncomingVehicle();

        verify(inputReaderUtil, Mockito.times(1)).readSelection();
        verify(parkingSpotDAO, Mockito.times(1)).getNextAvailableSlot(ParkingType.BIKE);
        verify(inputReaderUtil, Mockito.times(1)).readVehicleRegistrationNumber();
        verify(ticketDAO,Mockito.times(1)).isAlreadyInParking("ABCDEF");
        verify(parkingSpotDAO, Mockito.times(1)).updateParking(any(ParkingSpot.class));
        verify(ticketDAO).saveTicket(ticketCaptor.capture());

        Ticket ticketCaptorValue = ticketCaptor.getValue();
        
        assertEquals("ABCDEF", ticketCaptorValue.getVehicleRegNumber());
        assertNotNull(ticketCaptorValue.getInTime());
        assertNull(ticketCaptorValue.getOutTime());
        assertEquals(0, ticketCaptorValue.getPrice());
        assertFalse(outputStreamCaptor.toString().trim().contains("Heureux de vous revoir ! En tant qu’utilisateur régulier de notre parking, vous allez obtenir une remise de 5%"));
    }

    @Test
    public void testProcessIncomingBikeVehicleWithDiscount() throws Exception {
        when(inputReaderUtil.readSelection()).thenReturn(2);
        when(parkingSpotDAO.getNextAvailableSlot(any(ParkingType.class))).thenReturn(1);
        when(ticketDAO.isAlreadyInParking(anyString())).thenReturn(false);
        when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(false);
        when(ticketDAO.saveTicket(any(Ticket.class))).thenReturn(true);
        when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(5);

        parkingService.processIncomingVehicle();

        verify(inputReaderUtil, Mockito.times(1)).readSelection();
        verify(parkingSpotDAO, Mockito.times(1)).getNextAvailableSlot(ParkingType.BIKE);
        verify(inputReaderUtil, Mockito.times(1)).readVehicleRegistrationNumber();
        verify(ticketDAO,Mockito.times(1)).isAlreadyInParking("ABCDEF");
        verify(parkingSpotDAO, Mockito.times(1)).updateParking(any(ParkingSpot.class));
        verify(ticketDAO).saveTicket(ticketCaptor.capture());

        Ticket ticketCaptorValue = ticketCaptor.getValue();
        
        assertEquals("ABCDEF", ticketCaptorValue.getVehicleRegNumber());
        assertNotNull(ticketCaptorValue.getInTime());
        assertNull(ticketCaptorValue.getOutTime());
        assertEquals(0, ticketCaptorValue.getPrice());
        assertTrue(outputStreamCaptor.toString().trim().contains("Heureux de vous revoir ! En tant qu’utilisateur régulier de notre parking, vous allez obtenir une remise de 5%"));
    }

    @Test
    public void testProcessIncomingIfCarIsAlreadyInParking() throws Exception {
        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(parkingSpotDAO.getNextAvailableSlot(any(ParkingType.class))).thenReturn(1);
        when(ticketDAO.isAlreadyInParking(anyString())).thenReturn(true);
        
        parkingService.processIncomingVehicle();

        verify(inputReaderUtil, Mockito.times(1)).readSelection();
        verify(parkingSpotDAO, Mockito.times(1)).getNextAvailableSlot(ParkingType.CAR);
        verify(inputReaderUtil, Mockito.times(1)).readVehicleRegistrationNumber();
        verify(parkingSpotDAO, Mockito.never()).updateParking(any(ParkingSpot.class));
        verify(ticketDAO, Mockito.never()).saveTicket(any(Ticket.class));

        assertTrue(outputStreamCaptor.toString().trim().contains("Le véhicule est déjà dans le parking"));
    }

    @Test
    public void testProcessIncomingIfBikeIsAlreadyInParking() throws Exception {
        when(inputReaderUtil.readSelection()).thenReturn(2);
        when(parkingSpotDAO.getNextAvailableSlot(any(ParkingType.class))).thenReturn(1);
        when(ticketDAO.isAlreadyInParking(anyString())).thenReturn(true);
        
        parkingService.processIncomingVehicle();

        verify(inputReaderUtil, Mockito.times(1)).readSelection();
        verify(parkingSpotDAO, Mockito.times(1)).getNextAvailableSlot(ParkingType.BIKE);
        verify(inputReaderUtil, Mockito.times(1)).readVehicleRegistrationNumber();
        verify(parkingSpotDAO, Mockito.never()).updateParking(any(ParkingSpot.class));
        verify(ticketDAO, Mockito.never()).saveTicket(any(Ticket.class));

        assertTrue(outputStreamCaptor.toString().trim().contains("Le véhicule est déjà dans le parking"));
    }

    @Test
    public void testGetNextParkingNumberIfAvailable() {
        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(parkingSpotDAO.getNextAvailableSlot(any(ParkingType.class))).thenReturn(1);

        ParkingSpot result = parkingService.getNextParkingNumberIfAvailable();

        verify(inputReaderUtil, Mockito.times(1)).readSelection();
        verify(parkingSpotDAO,Mockito.times(1)).getNextAvailableSlot(any(ParkingType.class));

        assertEquals(1, result.getId());
        assertTrue(result.isAvailable());
    }

    @Test
    public void testGetNextParkingNumberIfAvailableParkingNumberNotFound() {
        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(parkingSpotDAO.getNextAvailableSlot(any(ParkingType.class))).thenReturn(0);

        ParkingSpot result = parkingService.getNextParkingNumberIfAvailable();

        verify(inputReaderUtil, Mockito.times(1)).readSelection();
        verify(parkingSpotDAO,Mockito.times(1)).getNextAvailableSlot(any(ParkingType.class));

        assertNull(result);
    }

    @Test
    public void testGetNextParkingNumberIfAvailableParkingNumberWrongArgument() {
        when(inputReaderUtil.readSelection()).thenReturn(3);

        ParkingSpot result = parkingService.getNextParkingNumberIfAvailable();

        verify(inputReaderUtil, Mockito.times(1)).readSelection();
        verify(parkingSpotDAO,Mockito.never()).getNextAvailableSlot(any(ParkingType.class));

        assertNull(result);
    }
}

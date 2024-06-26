/**
 * @author <Duong Viet Hoang - S3962514>
 */

package Controller;

import Model.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CustomerController {
    public String currentCustomerOrder = "default";
    private final ClaimController claimController;
    private static CustomerController instance;

    public static CustomerController getInstance() {
        if (instance == null) {
            instance = new CustomerController();
        }
        return instance;
    }

    public Customer getOne(String id) {
        return claimController.getListOfCustomers().stream().filter(customer -> customer.getId().equals(id)).findFirst().orElse(null);
    }

    public CustomerController() {
        this.claimController = ClaimController.getInstance();
    }

    public ArrayList<Customer> getListOfCustomers() {
        return claimController.getListOfCustomers();
    }

    public List<Customer> getAll() {
        return this.getListOfCustomers();
    }

    public boolean deleteCustomerPLC(String id) {
        Customer customer = this.getOne(id);
        if (customer != null) {
            InsuranceCard card = customer.getInsuranceCard();
            if (card != null) {
                InsuranceCardController.getInstance().delete(card.getCardNumber());
            }
            if (claimController.getListOfCustomers().removeIf(c -> c.getId().equals(id))) {
                // Iterate over the list of claims
                for (Claim claim : new ArrayList<>(claimController.getListOfClaims())) {
                    // Check if the claim's insured person or card number matches the customer or card that is being deleted
                    if (claim.getInsuredPerson() != null && claim.getInsuredPerson().getId().equals(customer.getId())) {
                        // Set the insured person to null
                        claim.setInsuredPerson(null);
                    }
                    if (claim.getCardNumber() != null && claim.getCardNumber().getCardNumber().equals(card.getCardNumber())) {
                        // Set the card number to null
                        claim.setCardNumber(null);
                    }
                }

                // If the customer is a PolicyHolder, delete all their dependents
                if (customer instanceof PolicyHolder) {
                    PolicyHolder policyHolder = (PolicyHolder) customer;
                    List<Dependent> dependentsCopy = new ArrayList<>(policyHolder.getDependents());
                    for (Dependent dependent : dependentsCopy) {
                        deleteCustomerPLC(dependent.getId());
                    }
                }

                if (claimController.getListOfCustomers().removeIf(c -> c.getId().equals(id))) {
                    // Iterate over the list of claims
                    for (Claim claim : claimController.getListOfClaims()) {
                        // Check if the claim's insured person or card number matches the customer or card that is being deleted
                        if (claim.getInsuredPerson().getId().equals(customer.getId()) || claim.getCardNumber().getCardNumber().equals(card.getCardNumber())) {
                            // Set the insured person and card number to null
                            claim.setInsuredPerson(null);
                            claim.setCardNumber(null);
                        }
                    }
                }

//                 If the customer is a Dependent, remove them from their PolicyHolder's list of dependents
                writeCustomersToFile();
                claimController.writeClaimsToFile1();
                return true;
            }
        }
        return false;
    }

    public boolean deleteCustomerDPD(String id) {
        Customer customer = this.getOne(id);
        if (customer != null) {
            InsuranceCard card = customer.getInsuranceCard();
            if (card != null) {
                InsuranceCardController.getInstance().delete(card.getCardNumber());
            }
            if (claimController.getListOfCustomers().removeIf(c -> c.getId().equals(id))) {
                // Iterate over the list of claims
                for (Claim claim : new ArrayList<>(claimController.getListOfClaims())) {
                    // Check if the claim's insured person or card number matches the customer or card that is being deleted
                    if (claim.getInsuredPerson() != null && claim.getInsuredPerson().getId().equals(customer.getId())) {
                        // Set the insured person to null
                        claim.setInsuredPerson(null);
                    }
                    if (claim.getCardNumber() != null && claim.getCardNumber().getCardNumber().equals(card.getCardNumber())) {
                        // Set the card number to null
                        claim.setCardNumber(null);
                    }
                }
//                 If the customer is a Dependent, remove them from their PolicyHolder's list of dependents
                if (customer instanceof Dependent) {
                    Dependent dependent = (Dependent) customer;
                    PolicyHolder policyHolder = dependent.getPolicyHolder();
                    policyHolder.getDependents().removeIf(d -> d.getId().equals(dependent.getId()));
                }

                if (claimController.getListOfCustomers().removeIf(c -> c.getId().equals(id))) {
                    // Iterate over the list of claims
                    for (Claim claim : claimController.getListOfClaims()) {
                        // Check if the claim's insured person or card number matches the customer or card that is being deleted
                        if (claim.getInsuredPerson().getId().equals(customer.getId()) || claim.getCardNumber().getCardNumber().equals(card.getCardNumber())) {
                            // Set the insured person and card number to null
                            claim.setInsuredPerson(null);
                            claim.setCardNumber(null);
                        }
                    }
                }
                writeCustomersToFile();
                claimController.writeClaimsToFile1();
                return true;
            }
        }
        return false;
    }

    public PolicyHolder addPolicyHolder(String fullName, InsuranceCard insuranceCard, List<Claim> claims, List<Dependent> dependents) {
        String id = generateUniqueCustomerID();
        PolicyHolder policyHolder = new PolicyHolder(id, fullName, insuranceCard, claims, dependents);
        this.getListOfCustomers().add(policyHolder);
        writeCustomersToFile();
        return policyHolder;
    }

    public Dependent addDependent(String fullName, InsuranceCard insuranceCard, List<Claim> claims) {
        String id = generateUniqueCustomerID();
        Dependent dependent = new Dependent(id, fullName, insuranceCard, claims);
        this.getListOfCustomers().add(dependent);
        writeCustomersToFile();
        return dependent;
    }

    private synchronized String generateUniqueCustomerID() {
        int maxAssignedNumber = 0;
        for (Customer customer : claimController.getListOfCustomers()) {
            String containerID = customer.getId();
            if (containerID.startsWith("c-")) {
                try {
                    // Correctly parsing the numeric part of the ID after "c-"
                    int number = Integer.parseInt(containerID.substring(2));
                    maxAssignedNumber = Math.max(maxAssignedNumber, number);
                } catch (NumberFormatException e) {
                    // Handle parsing error if necessary
                }
            }
        }

        // Increment to get the next number
        int nextNumber = maxAssignedNumber + 1;
        // Generate the next ID, adjusting for length if necessary
        String nextID = String.format("c-%07d", nextNumber);

        return nextID;
    }

    public void sortCustomerByNumberOfClaim(boolean ascending) {
        if (ascending) {
            claimController.getListOfCustomers().sort(Comparator.comparingInt(customer -> ((Customer)customer).getClaims().size()));
            currentCustomerOrder = "claim count from least to most";
        } else {
            claimController.getListOfCustomers().sort(Comparator.comparingInt(customer -> ((Customer)customer).getClaims().size()).reversed());
            currentCustomerOrder = "claim count from most to least";
        }
    }

    public void writeCustomersToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter("dataFile/customers.txt"))) {
            // Write the CSV header
            writer.println("ID,Full Name,Insurance Card,List Of Claims,List Of Dependents");

            // Write customer records
            for (Customer customer : claimController.getListOfCustomers()) {
                // Initializing dependents string to be empty
                String dependents = "";
                if (customer instanceof PolicyHolder) {
                    List<Dependent> listOfDependents = ((PolicyHolder) customer).getDependents();
                    // Check if listOfDependents is not null and not empty before proceeding
                    if (listOfDependents != null && !listOfDependents.isEmpty()) {
                        dependents = listOfDependents.stream()
                                .map(Dependent::getId)
                                .collect(Collectors.joining(";"));
                        dependents = "[" + dependents + "]";
                    } else {
                        // Add empty brackets if there are no dependents to ensure the record has 5 elements
                        dependents = "[]";
                    }
                }

                // Preparing the claims string, including brackets regardless of content
                String claims = "[" + customer.getClaims().stream().map(Claim::getId).collect(Collectors.joining(";")) + "]";

                // Formatting the output line for PolicyHolder and Dependent differently
                String outputLine;
                if (customer instanceof PolicyHolder) {
                    outputLine = String.format("%s,%s,%s,%s,%s",
                            customer.getId(),
                            customer.getFullName(),
                            customer.getInsuranceCard().getCardNumber(),
                            claims,
                            dependents); // Always include dependents field for PolicyHolder
                } else {
                    // For Dependents, no need to add an empty dependents field
                    outputLine = String.format("%s,%s,%s,%s",
                            customer.getId(),
                            customer.getFullName(),
                            customer.getInsuranceCard().getCardNumber(),
                            claims);
                }

                writer.println(outputLine);
            }
        } catch (IOException e) {
//            e.printStackTrace();
        }
    }


}
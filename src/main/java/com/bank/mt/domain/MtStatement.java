package com.bank.mt.domain;

/**
 * Parsed representation of an MT message extracted from raw SWIFT format.
 */
public class MtStatement {

    private String messageType;
    private String accountNumber;
    private String statementNumber;
    private int pageNumber;
    private int totalPages;
    private String senderBic;
    private String receiverBic;
    private String receiverBicBranch;
    private String transactionReference;
    private String rawMessage;

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getStatementNumber() { return statementNumber; }
    public void setStatementNumber(String statementNumber) { this.statementNumber = statementNumber; }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public String getSenderBic() { return senderBic; }
    public void setSenderBic(String senderBic) { this.senderBic = senderBic; }

    public String getReceiverBic() { return receiverBic; }
    public void setReceiverBic(String receiverBic) { this.receiverBic = receiverBic; }

    public String getReceiverBicBranch() { return receiverBicBranch; }
    public void setReceiverBicBranch(String receiverBicBranch) { this.receiverBicBranch = receiverBicBranch; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String rawMessage) { this.rawMessage = rawMessage; }

    public boolean isMultiPage() {
        return totalPages > 1;
    }
}

import com.sap.gateway.ip.core.customdev.util.Message;
import groovy.xml.MarkupBuilder;

def Message processData(Message message) {
    def body = message.getBody(java.lang.String) as String;
    def xml = new XmlSlurper().parseText(body);
    
    def batchRef = xml.Header.BatchReference.text();
    
    // Filter condition: Keep only orders with Amount >= 150.00
    def validOrders = xml.Orders.Order.findAll { it.Amount.text().toDouble() >= 150.00 }
    
    // Add tracking headers for the viewer dashboard
    def messageLog = messageLogFactory.getMessageLog(message);
    if (messageLog != null) {
        messageLog.addCustomHeaderProperty("BatchId", batchRef);
        messageLog.addCustomHeaderProperty("Log-Integration-Source", "Salesforce-Bulk");
        messageLog.addCustomHeaderProperty("FilteredCount", validOrders.size().toString());
    }
    
    def writer = new StringWriter();
    def xmlBuilder = new MarkupBuilder(writer);
    
    xmlBuilder.FilteredProcessedBatch {
        Metadata {
            BatchId(batchRef)
            ProcessedTimestamp(new Date().format("yyyy-MM-dd'T'HH:mm:ss"))
            RecordsProcessed(validOrders.size())
        }
        ItemDetails {
            validOrders.each { ord ->
                Item {
                    ReferenceId(ord.OrderId.text())
                    Client(ord.CustomerName.text())
                    NetValue(ord.Amount.text())
                    CurrencyKey(ord.Currency.text())
                }
            }
        }
    }
    
    def transformedPayload = writer.toString();
    message.setBody(transformedPayload);
    
    if (messageLog != null) {
        messageLog.addAttachmentAsString("Filtered_Bulk_Result", transformedPayload, "application/xml");
    }
    
    return message;
}
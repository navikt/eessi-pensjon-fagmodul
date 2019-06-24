package no.nav.eessi.eessifagmodul.services.pensjonsinformasjon

import org.springframework.stereotype.Component
import org.springframework.util.ResourceUtils
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

private const val XS = "http://www.w3.org/2001/XMLSchema"

@Component
class RequestBuilder {

    private final val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    private final val documentBuilder = documentBuilderFactory.newDocumentBuilder()

    private val fullRequestDocument: Document

    init {
        fullRequestDocument = documentBuilder.parse(ResourceUtils.getURL("classpath:schemas/pensjonsinformasjon-xsd/pensjonsinformasjon/v1/v1.Pensjonsinformasjon.xsd").openStream())
    }

    fun addPensjonsinformasjonElement(document: Document, informasjonsType: InformasjonsType) {
        val baseElement = document.documentElement
        addToElementList(document, informasjonsType, baseElement)
    }

    /* Legger til <element> i \schema\Pensjonsinformasjon\all */
    private fun addToElementList(document: Document, informasjonsType: InformasjonsType, baseElement: Element) {
        val elementElement = document.createElementNS(XS, "xs:element")

        elementElement.setAttribute("name", informasjonsType.elementName())
        elementElement.setAttribute("type", "${informasjonsType.elementName()}:${informasjonsType.typeName()}")
        elementElement.setAttribute("minOccurs", "0")

        val allList = baseElement.getElementsByTagNameNS(XS, "all").item(0) as Element
        allList.appendChild(elementElement)
    }

    fun getBaseRequestDocument(): Document {
        val originalNode = fullRequestDocument.documentElement
        val baseDocument = documentBuilder.newDocument()

        val copiedNode = baseDocument.importNode(originalNode, true)
        baseDocument.appendChild(copiedNode)

        // Remove all <xs:element>-nodes from <xs:all>
        val elements = baseDocument.getElementsByTagNameNS(XS, "all").item(0)
        while (elements.hasChildNodes())
            elements.removeChild(elements.firstChild)
        return baseDocument
    }
}

fun Document.documentToString(): String {
    val tf = TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        setOutputProperty(OutputKeys.INDENT, "yes")
        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
    }
    val out = StringWriter()
    tf.transform(DOMSource(this), StreamResult(out))
    return out.toString()
}

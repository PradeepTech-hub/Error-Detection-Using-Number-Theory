# 📡 Analysis of Error Detection Techniques Using Number Theory

## 📘 Project Description

This project implements an error detection system for data communication using Number Theory concepts.  
It applies modular arithmetic with prime numbers to generate checksum values that verify data integrity during transmission in Computer Networks.

The system visually simulates the sender, transmission channel, and receiver to demonstrate how errors occur and how they are detected.

---

## 🎯 Project Objectives

- Implement checksum-based error detection using modular arithmetic  
- Apply Number Theory concepts in Computer Networks  
- Simulate real transmission errors  
- Visualize data flow between sender and receiver  
- Compare mathematical checksum principle with CRC concept  

---

## 🧮 Mathematical Concept Used

Checksum generation is based on:

Checksum = Data mod Key

Where:
- Data represents the transmitted message  
- Key is a prime number  

The remainder uniquely identifies the data and helps detect corruption.

---

## 🌐 Computer Network Working

Sender → Channel → Receiver

### Process:

1. Sender enters data and generates checksum  
2. Data is transmitted through simulated channel  
3. Channel may introduce errors (digit flip, delta change, random noise)  
4. Receiver recalculates checksum  
5. Checksums are compared  

✔ Same → No Error  
❌ Different → Error Detected  

---

## 🖥 Application Features

- Java Swing graphical user interface  
- Sender and receiver simulation  
- Checksum computation in real time  
- Error injection modes  
- Large data handling  
- Clear execution flow  

---

## 📚 Concepts Implemented

### Number Theory:
- Modular arithmetic  
- Prime numbers  
- Remainder theorem  
- Modular congruence  

### Computer Networks:
- Error detection  
- Checksum mechanism  
- Data transmission model  
- Channel noise simulation  

---

## 🚀 How to Run the Project

### Requirements:
- Java JDK installed  
- VS Code or any Java IDE  


---

## 🧪 Sample Execution

Data: 987654321  
Key: 13  

Checksum: 9  

If corrupted:

987654322 → Checksum becomes 10 → Error detected  

---

## 🌍 Real World Use Cases

- Network packet verification  
- File transfer integrity  
- Wireless communication  
- Storage systems  
- Embedded safety systems  

---

## 📈 Future Enhancements

- Implement CRC algorithm  
- Binary packet processing  
- Error correction techniques  
- Performance analysis  
- Visualization of error rates  

---

## 🏁 Conclusion

This project demonstrates how Number Theory can be applied to build effective checksum-based error detection mechanisms in Computer Networks.  
It provides a clear simulation of data transmission and corruption detection.

---

## 👨‍💻 Author

Pradeep M


1. Clone the repository:

# K-Resilient-Distributed-System
It uses AWS Elastic Beanstalk together with UDP networking, to build a distributed, scalable and fault-tolerant session maintenance website.
AWS Elastic Beanstalk is used to create and maintain a load-balanced set of application servers running Apache Tomcat. 
* Servlets/JSPs for processing client requests.
* A distributed session state database analogous to SSM: each server node will have a local in-memory session data table (SessTbl) exposed through a Remote Procedure Call server interface, and each server will have a client RPC “stub” to access the SessTbls of other server nodes.
* A gossip-based approximate group membership service done with AWS SimpleDB, so with high probability every server knows the address of all the other servers.

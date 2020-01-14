#### Using Managed System Identities on K8S pods
This project is intended as a showcase on how to use [Managed System Identities](https://docs.microsoft.com/en-us/azure/active-directory/managed-identities-azure-resources/overview) on 
Azure Kubernetes Service (AKS) with a Java Spring Boot app. This repository is based on https://github.com/Azure/aad-pod-identity, 
and most (if not all) of the information is available and maintained there. 

In order to illustrate the concepts a simple Java app is deployed on an AKS cluster, that acts as a proxy
for an Azure Key Vault. Note that there are ways of configuring Azure Key Vault to provide [secrets as configuration](https://docs.microsoft.com/en-us/azure/java/spring-framework/configure-spring-boot-starter-java-app-with-azure-key-vault),
but for the purpose of this project we'll directly access it with a ```KeyVaultClient```.

The steps below assume that you've already created the necessary resources using your preferred method 
(azure-cli, ARM templates, Terraform etc.), but all further configuration will be done with azure-cli and 
obviusly ```kubectl``` will be used to manage the AKS cluster. So, the code below assumes that azure-cli as well as 
```kubectl``` is installed. Also to build the Java code, ```mvn``` is needed.

> Note that the snippets below assume that you're on the bash shell, otherwise please amend the commands to match your 
> environment.

##### Basics
Assuming that you've already successfully created an AKS cluster, Azure Container Registry 
([attached to the cluster](https://docs.microsoft.com/en-us/azure/aks/cluster-container-registry-integration)),
Azure Key Vault and stored the names of these resources in the following environment variables
```bash
RG=...  # name of the resource group where all the resources are deployed
AKS=... # name of the AKS cluster
ACR=...  # name of the container registry, without the azurecr.io suffix
KV=...  # name of the Key Vault
```

If you haven't configured access to your cluster, you can easily do that with the following cli
command
```bash
az aks get-credentials -g $RG -n $AKS
```

##### Preparing the cluster
As described in the aad-pod-identity repository, a few resources need to be installed on the cluster to enable 
Managed System Identities. If you've configured your cluster to be RBAC aware, you can install the 
necessary resources using the corresponding template.

```bash
kubectl apply -f https://raw.githubusercontent.com/Azure/aad-pod-identity/master/deploy/infra/deployment-rbac.yaml
```

##### Identities
The identity we'll configure needs to be created in the resource group in which worker nodes are deployed. This is
different from the resource group where AKS is created. In order to get that you can run this command and store the 
result in a variable 

```bash
MC_RG=`az aks show -g $RG -n $AKS --query nodeResourceGroup -o tsv`
```

We'll need the client id later for configuring access, so let's store that in a variable.
```bash
CL_ID=`az identity create -n demo-aad1 -g $MC_RG --query clientId -o tsv`
```

If you already have some secrets in your key vault, you can skip this, otherwise put some sample
data in there.
```bash
az keyvault secret set --vault-name $KV --name mySecret --value 42
```

Now let's give permissions to the generated identity to access secrets. Note that we're using the 
client id for this purpose. 
```bash
az keyvault set-policy -n $KV --spn $CL_ID --secret-permissions list get
```

##### Application

The application is pretty trivial, we'll be just building a Spring Boot app and deploy that 
in the container registry. Note that this step doesn't require docker to be installed on your local 
machine as building happens on the container registry. Make sure that you're running this command
from the top level directory of this repository.

```bash
mvn clean package
az acr build -t demo/key-vault-pod-identity:1.0 -r $ACR .
```

##### Bringing things together
First step is to replace the placeholders in the configuration files. From the top level directory run this
```bash
cd k8s
sed -i -e "s/<KV>/$KV/g" -e "s/<ACR>/$ACR/g" deployment.yaml
SUB_ID=`az account show --query id -o tsv`
sed -i -e "s/<SUBSCRIPTION_ID>/$SUB_ID/g" -e "s/<RESOURCE_GROUP>/$MC_RG/g" -e "s/<CLIENT_ID>/$CL_ID/g" aadpodidentity.yaml
```

Now we can deploy the required components and the application
```bash
kubectl apply -f aadpodidentity.yaml 
kubectl apply -f aadpodidentitybinding.yaml 
kubectl apply -f deployment.yaml 
```

##### Testing
After proxying the pod, you can access the application at localhost:8080
```bash
POD=`kubectl get pods -l app=kv-demo -o name`
kubectl port-forward $POD 8080:8080
```

If you're using the sample secret, you should see something like that
```console
$ curl localhost:8080/secret/mySecret
42
```
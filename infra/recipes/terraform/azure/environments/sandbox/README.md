# Initialize and Run Azure Sandbox
Ensure you have a Azure account. As part of sandbox we use Terraform to provision a resource group, storage account, and blob container within that storage account. 

## Creating an Azure Account

Create an Azure account [here](https://azure.microsoft.com/en-us/pricing/purchase-options/azure-account). If you haven't created one already, you can use the free trial to get free credits. Create a subscription using this free trial, or with another option if the free trial is over.

### Creating a Storage Account

A storage account must be created to hold the backend terraform files. Create a new storage account using an arbitrary resource group. It is typical to name these resources starting with `tfbackend`. Then, within this storage account, create a new blob container. Note the resource group name, storage account name, and blob container name. Also note the storage account key, which can be found in the "Access keys" sidebar option in the storage account. These variables will be used when configuring the backend.

## Azure Login

Install the [Azure CLI](https://learn.microsoft.com/en-us/cli/azure/), and ensure that you are logged in with your Azure account. You can login in multiple ways.

### Using username/password

This is the simplest version. Run `az login` and login to your account on a web browser.

### Using Service Principal

Following the steps [here](https://learn.microsoft.com/en-us/cli/azure/authenticate-azure-cli-service-principal), you can run `az login --service-principal -u <app-id> -p <password-or-cert> --tenant <tenant>` to gain more control over your subscription and account.

## Terraform Backend
 
Terraform backend state can be stored locally or in Azure blob store. To configure this you will be setting the variables like those in `infra/recipes/terraform/azure/backend.tfvars.template`. These variables are the ones found when setting up the storage account.

### Using Environment Variables
You could set the environment variables for variables to stored terraform backend state. For example, to initialize `storage_account_name` you can set the following environment variable.
`export TF_VAR_storage_account_name="foobar"`

### Using tfvars
1. Create a copy of the file `backend.tfvars.template` into your environment directory by running
`cp backend.tfvars.template ./environments/<environment-name>/backend.tfvars`.
2. Edit the backend.tfvars to configure the necessary TF backend state.

## OpenHouse  - Build

Follow the steps described in [SETUP.md](SETUP.md#build-containers) to build JAR artifacts locally for OpenHouse services using Gradle. Currently, running only tables and housetables service is supported in Azure.


## Deployment

### Installation

Install the `terraform` CLI by following [this](https://developer.hashicorp.com/terraform/tutorials/aws-get-started/install-cli) link.

### Initialization

Run `terraform init` when setting up a new configuration, changing backend settings, or modifying provider versions. You don't need to run it for regular operations like `terraform plan` and `terraform apply` unless the configuration changes.

_If using tfvars_
`terraform init -backend-config="./backend.tfvars"`

_If using environment variables_
`terraform init`

### Planning & Applying

`terraform plan` to see the plan generated by terraform for the specified deployment configuration. If you are satisfied with the plan, run `terraform apply` to apply the configuration.

#### Docker Images - Publish

You can use the Terraform code provided in [Azure Container Terraform](infra/recipes/terraform/azure/environments/container) to build and push the images to ACR.

Run the following three commands for ACR publishing

1. `terraform init` : Run it first time to setup tfbackend
2. `terraform plan` : Plan the Terraform deployment
3. `terraform apply` : Apply the Terraform deployment

#### Rest of Infra - Setup

As part of bringing OpenHouse in Azure, you will need to setup other Azure services like AKS, MySQL, Storage. You can use the Terraform code provided in [Azure Sandbox Terraform](infra/recipes/terraform/azure/environments/sandbox) to setup rest of infra that is required for successfully bringing up OpenHouse.

Run the following three commands for rest of infra setup

1. `terraform init` : Run it first time to setup tfbackend
2. `terraform plan` : Plan the Terraform deployment
3. `terraform apply` : Apply the Terraform deployment

## Deconfiguration

Run `terraform destroy` once you are done to tear down the services you have created.

# Access Created Services

Once you have successfully run the above steps so the sandbox has been run, you can now access the pods in AKS in your local terminal by ensuring you are logged in to the Azure CLI with `az login` and then running
`az aks get-credentials --resource-group <resource-group> --name <cluster-name>`. You can then run any `kubectl` commands.

## Make requests to services

To make requests to the services, set up port forwarding by running `kubectl port-forward <pod-name> 8080:<destination-port>`. You can get the pod name by running `kubectl get pods`. Then, you can make requests to HTS and Tables service via `http://localhost:<destination-port>`. If you want to make requests to both HTS and Tables services you will need to forward to two separate ports.
